#!/usr/bin/env node

/**
 * Downloads fuel price data for Spain and Portugal and processes it into
 * the same per-"department" JSON files as the French pipeline, so the app
 * can serve all three countries through a single code path.
 *
 * Spain    — Ministerio (MINETUR) REST API, all public road stations:
 *            https://sedeaplicaciones.minetur.gob.es/ServiciosRESTCarburantes/PreciosCarburantes/EstacionesTerrestres/
 *            Grouped by province → public/data/departments/ES-{IDProvincia}.json
 * Portugal — DGEG "Preços de combustíveis online" API, one request per fuel:
 *            https://precoscombustiveis.dgeg.gov.pt/api/PrecoComb/PesquisarPostos
 *            Grouped by district → public/data/departments/PT-{district-slug}.json
 *
 * Station ids are offset (ES +100M, PT +200M) so they can never collide with
 * French gouv.fr ids (8 digits max) — favorites and deeplinks keep working.
 *
 * Bounding boxes of the new groups are merged into public/data/dept-bbox.json
 * (keys of the other countries are preserved). Run after process-data.mjs.
 */

import { writeFileSync, mkdirSync, existsSync, readFileSync, readdirSync, unlinkSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = join(__dirname, '..');
const DATA_DIR = join(ROOT, 'public', 'data');
const DEPT_DIR = join(DATA_DIR, 'departments');
const BBOX_PATH = join(DATA_DIR, 'dept-bbox.json');

const ES_URL =
  'https://sedeaplicaciones.minetur.gob.es/ServiciosRESTCarburantes/PreciosCarburantes/EstacionesTerrestres/';
const PT_URL = 'https://precoscombustiveis.dgeg.gov.pt/api/PrecoComb/PesquisarPostos';

// Offsets keep foreign ids clear of French gouv.fr ids (≤ 8 digits).
const ES_ID_OFFSET = 100_000_000;
const PT_ID_OFFSET = 200_000_000;

// Spanish price columns → app fuel names (same semantics as the French set).
const ES_FUEL_COLUMNS = {
  'Precio Gasoleo A': 'Gazole',
  'Precio Gasolina 95 E5': 'SP95',
  'Precio Gasolina 95 E10': 'E10',
  'Precio Gasolina 98 E5': 'SP98',
  'Precio Gasolina 95 E85': 'E85',
  'Precio Gases licuados del petróleo': 'GPLc',
};

// DGEG fuel type ids → app fuel names (see GetTiposCombustiveis endpoint).
const PT_FUELS = [
  { id: 2101, fuel: 'Gazole' }, // Gasóleo simples
  { id: 3201, fuel: 'SP95' }, // Gasolina simples 95
  { id: 3400, fuel: 'SP98' }, // Gasolina 98
  { id: 1120, fuel: 'GPLc' }, // GPL Auto
];

// Display names for the most common Iberian brands (source data is ALL CAPS).
const BRAND_NORMALIZE = {
  REPSOL: 'Repsol',
  CEPSA: 'Cepsa',
  MOEVE: 'Moeve',
  GALP: 'Galp',
  BALLENOIL: 'Ballenoil',
  PLENERGY: 'Plenergy',
  PLENOIL: 'Plenoil',
  SHELL: 'Shell',
  PETROPRIX: 'Petroprix',
  PETRONOR: 'Petronor',
  CARREFOUR: 'Carrefour',
  BP: 'BP',
  AVIA: 'Avia',
  Q8: 'Q8',
  CAMPSA: 'Campsa',
  ALCAMPO: 'Alcampo',
  ENI: 'Eni',
  EROSKI: 'Eroski',
  ESSO: 'Esso',
  TOTAL: 'TotalEnergies',
  TOTALENERGIES: 'TotalEnergies',
  'INTERMARCHÉ': 'Intermarché',
  INTERMARCHE: 'Intermarché',
  'ALVES BANDEIRA': 'Alves Bandeira',
  PRIO: 'Prio',
  'PINGO DOCE': 'Pingo Doce',
  AUCHAN: 'Auchan',
  LECLERC: 'Leclerc',
  'E.LECLERC': 'Leclerc',
  'OZ ENERGIA': 'OZ Energia',
  'REDE ENERGIA': 'Rede Energia',
  TFUEL: 'TFuel',
  GASPE: 'Gaspe',
  FREITAS: 'Freitas',
  BONAREA: 'BonÀrea',
  MEROIL: 'Meroil',
  BEROIL: 'Beroil',
  GASEXPRESS: 'GasExpress',
  VALCARCE: 'Valcarce',
  ESCLATOIL: 'EsclatOil',
  DISA: 'Disa',
};

function normalizeBrand(raw) {
  const trimmed = (raw ?? '').trim();
  if (!trimmed) return undefined;
  // Unbranded / station-number labels are worse than no brand at all.
  if (/^gen[ée]rico$/i.test(trimmed)) return undefined;
  if (/^n[ºo°.]?\s*[\d.,-]+$/i.test(trimmed)) return undefined;
  const known = BRAND_NORMALIZE[trimmed.toUpperCase()];
  if (known) return known;
  // Fallback: Title Case each word of the ALL-CAPS source label.
  return trimmed
    .toLowerCase()
    .replace(/(^|[\s\-.'])\p{L}/gu, (m) => m.toUpperCase());
}

/** "1,499" / "1,659 €" → 1.499 (undefined when empty or unparsable). */
function parsePrice(raw) {
  if (!raw) return undefined;
  const n = parseFloat(String(raw).replace(/[^\d,.-]/g, '').replace(',', '.'));
  if (!isFinite(n) || n <= 0 || n > 5) return undefined;
  return Math.round(n * 1000) / 1000;
}

function parseCoord(raw) {
  const n = typeof raw === 'number' ? raw : parseFloat(String(raw).replace(',', '.'));
  return isFinite(n) && n !== 0 ? n : undefined;
}

/** "Viana do Castelo" → "viana-do-castelo", "Évora" → "evora". */
function slugify(str) {
  return str
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

async function fetchJSON(url, timeoutMs = 180_000, maxAttempts = 4) {
  let lastErr;
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    try {
      const res = await fetch(url, {
        headers: { Accept: 'application/json' },
        signal: AbortSignal.timeout(timeoutMs),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status} for ${url}`);
      return await res.json();
    } catch (err) {
      lastErr = err;
      if (attempt < maxAttempts) {
        const delay = attempt * 10_000;
        console.warn(`  fetch failed (${err.message}) — retrying in ${delay / 1000}s...`);
        await new Promise((r) => setTimeout(r, delay));
      }
    }
  }
  throw lastErr;
}

// ─── Spain ───────────────────────────────────────────────

async function processSpain() {
  console.log('Fetching Spanish stations (MINETUR)...');
  const data = await fetchJSON(ES_URL);
  const list = data?.ListaEESSPrecio;
  if (!Array.isArray(list) || list.length === 0) {
    throw new Error('MINETUR payload has no ListaEESSPrecio');
  }

  // Single snapshot timestamp for the whole payload: "14/07/2026 14:43:33".
  let snapshotDate = new Date().toISOString().split('T')[0];
  const m = String(data.Fecha ?? '').match(/^(\d{2})\/(\d{2})\/(\d{4})/);
  if (m) snapshotDate = `${m[3]}-${m[2]}-${m[1]}`;

  const groups = {};
  for (const row of list) {
    // 'P' = public sale; anything else is restricted (fleets, cooperatives).
    if (row['Tipo Venta'] && row['Tipo Venta'] !== 'P') continue;

    const lat = parseCoord(row['Latitud']);
    const lng = parseCoord(row['Longitud (WGS84)']);
    // Mainland + Baleares + Canarias sanity window.
    if (lat === undefined || lng === undefined || lat < 27 || lat > 44.5 || lng < -18.5 || lng > 5) continue;

    const fuels = {};
    for (const [column, fuel] of Object.entries(ES_FUEL_COLUMNS)) {
      const p = parsePrice(row[column]);
      if (p !== undefined) fuels[fuel] = { p, d: snapshotDate };
    }
    if (Object.keys(fuels).length === 0) continue;

    const province = String(row['IDProvincia'] ?? '').padStart(2, '0');
    const dept = `ES-${province}`;
    if (!groups[dept]) groups[dept] = [];

    const entry = {
      id: ES_ID_OFFSET + parseInt(row['IDEESS'], 10),
      lat,
      lng,
      addr: row['Dirección']?.trim() ?? '',
      city: row['Localidad']?.trim() ?? '',
      cp: row['C.P.']?.trim() ?? '',
      fuels,
    };
    const brand = normalizeBrand(row['Rótulo']);
    if (brand) entry.brand = brand;
    if (/24\s*H/i.test(row['Horario'] ?? '')) entry.h24 = true;
    groups[dept].push(entry);
  }

  const total = Object.values(groups).reduce((s, a) => s + a.length, 0);
  console.log(`Spain: ${total} stations in ${Object.keys(groups).length} provinces.`);
  return groups;
}

// ─── Portugal ────────────────────────────────────────────

async function processPortugal() {
  console.log('Fetching Portuguese stations (DGEG)...');
  const stations = new Map();

  for (const { id, fuel } of PT_FUELS) {
    const url = `${PT_URL}?idsTiposComb=${id}&qtdPorPagina=50000&pagina=1`;
    const data = await fetchJSON(url);
    const rows = data?.resultado;
    if (!Array.isArray(rows)) {
      console.warn(`  DGEG returned no results for fuel ${fuel} (${id}) — skipping.`);
      continue;
    }
    for (const row of rows) {
      const lat = parseCoord(row.Latitude);
      const lng = parseCoord(row.Longitude);
      // Mainland + Açores/Madeira sanity window.
      if (lat === undefined || lng === undefined || lat < 29 || lat > 43 || lng < -32 || lng > -6) continue;
      const p = parsePrice(row.Preco);
      if (p === undefined) continue;

      const stationId = PT_ID_OFFSET + row.Id;
      let station = stations.get(stationId);
      if (!station) {
        station = {
          id: stationId,
          lat,
          lng,
          addr: row.Morada?.trim() ?? '',
          city: row.Localidade?.trim() || row.Municipio?.trim() || '',
          cp: row.CodPostal?.trim() ?? '',
          fuels: {},
          district: row.Distrito?.trim() || 'outros',
        };
        const brand = normalizeBrand(row.Marca);
        if (brand) station.brand = brand;
        stations.set(stationId, station);
      }
      // "2026-07-06 17:25" → "2026-07-06"
      const d = String(row.DataAtualizacao ?? '').split(' ')[0] || new Date().toISOString().split('T')[0];
      station.fuels[fuel] = { p, d };
    }
    console.log(`  ${fuel}: ${rows.length} rows.`);
  }

  if (stations.size === 0) throw new Error('DGEG returned no stations at all');

  const groups = {};
  for (const station of stations.values()) {
    const dept = `PT-${slugify(station.district)}`;
    const { district: _district, ...entry } = station;
    if (!groups[dept]) groups[dept] = [];
    groups[dept].push(entry);
  }

  console.log(`Portugal: ${stations.size} stations in ${Object.keys(groups).length} districts.`);
  return groups;
}

// ─── Output ──────────────────────────────────────────────

function computeBbox(deptStations) {
  let minLat = Infinity, maxLat = -Infinity, minLng = Infinity, maxLng = -Infinity;
  for (const s of deptStations) {
    if (s.lat < minLat) minLat = s.lat;
    if (s.lat > maxLat) maxLat = s.lat;
    if (s.lng < minLng) minLng = s.lng;
    if (s.lng > maxLng) maxLng = s.lng;
  }
  if (!isFinite(minLat)) return null;
  return [
    Math.round(minLat * 1000) / 1000,
    Math.round(maxLat * 1000) / 1000,
    Math.round(minLng * 1000) / 1000,
    Math.round(maxLng * 1000) / 1000,
  ];
}

/** Writes {prefix}-*.json dept files, dropping stale ones from previous runs. */
function writeCountry(prefix, groups) {
  mkdirSync(DEPT_DIR, { recursive: true });
  const fresh = new Set(Object.keys(groups).map((d) => `${d}.json`));
  for (const file of readdirSync(DEPT_DIR)) {
    if (file.startsWith(`${prefix}-`) && !fresh.has(file)) {
      unlinkSync(join(DEPT_DIR, file));
      console.log(`  removed stale ${file}`);
    }
  }

  const bboxes = {};
  for (const [dept, deptStations] of Object.entries(groups)) {
    writeFileSync(join(DEPT_DIR, `${dept}.json`), JSON.stringify(deptStations));
    console.log(`  ${dept}.json → ${deptStations.length} stations`);
    const bbox = computeBbox(deptStations);
    if (bbox) bboxes[dept] = bbox;
  }
  return bboxes;
}

/** Merge bbox entries into dept-bbox.json, replacing only the given prefixes. */
function mergeBbox(entriesByPrefix) {
  let existing = {};
  if (existsSync(BBOX_PATH)) {
    try {
      existing = JSON.parse(readFileSync(BBOX_PATH, 'utf-8'));
    } catch {
      existing = {};
    }
  }
  for (const [prefix, entries] of Object.entries(entriesByPrefix)) {
    for (const key of Object.keys(existing)) {
      if (key.startsWith(`${prefix}-`)) delete existing[key];
    }
    Object.assign(existing, entries);
  }
  writeFileSync(BBOX_PATH, JSON.stringify(existing));
  console.log(`Merged dept-bbox.json (${Object.keys(existing).length} entries).`);
}

async function main() {
  console.log('=== FuelRadar — Spain & Portugal Data Pipeline ===\n');

  const bboxUpdates = {};
  let failures = 0;

  try {
    bboxUpdates.ES = writeCountry('ES', await processSpain());
  } catch (err) {
    failures++;
    console.warn(`Spain pipeline failed (keeping previous files): ${err.message}`);
  }

  try {
    bboxUpdates.PT = writeCountry('PT', await processPortugal());
  } catch (err) {
    failures++;
    console.warn(`Portugal pipeline failed (keeping previous files): ${err.message}`);
  }

  if (Object.keys(bboxUpdates).length > 0) {
    mergeBbox(bboxUpdates);
  }

  if (failures === 2) {
    console.error('Both country pipelines failed.');
    process.exit(1);
  }
  console.log('\nDone!');
}

main().catch((err) => {
  console.error('FATAL:', err);
  process.exit(1);
});
