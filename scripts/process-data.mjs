#!/usr/bin/env node

/**
 * Downloads fuel price data from prix-carburants.gouv.fr
 * and processes it into per-department JSON files.
 *
 * Source: https://donnees.roulez-eco.fr/opendata/instantane
 * Format: ZIP containing XML
 */

import { writeFileSync, mkdirSync, existsSync, readFileSync, unlinkSync, createWriteStream } from 'fs';
import { pipeline } from 'stream/promises';
import { createReadStream } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';
import { createUnzip } from 'zlib';
import { createInterface } from 'readline';

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = join(__dirname, '..');
const DATA_DIR = join(ROOT, 'public', 'data');
const DEPT_DIR = join(DATA_DIR, 'departments');
const BRANDS_CACHE = join(ROOT, 'brands-cache.json');

const SOURCE_URL = 'https://donnees.roulez-eco.fr/opendata/instantane';
const OVERPASS_URL = 'https://overpass-api.de/api/interpreter';
const ZIP_PATH = join(ROOT, '.tmp-fuel-data.zip');
const XML_PATH = join(ROOT, '.tmp-fuel-data.xml');

// Fuel type mapping from XML names to our short names
const FUEL_MAP = {
  1: 'Gazole',
  2: 'SP95',
  3: 'E85',
  4: 'GPLc',
  5: 'E10',
  6: 'SP98',
};

// Brand normalization (duplicated from src/utils/brands.ts for plain JS usage)
const BRAND_NORMALIZE = {
  'Total': 'TotalEnergies',
  'Total Access': 'TotalEnergies',
  'TotalEnergies Access': 'TotalEnergies',
  'total': 'TotalEnergies',
  'TOTAL': 'TotalEnergies',
  'TOTALENERGIES': 'TotalEnergies',
  'TotalEnergies': 'TotalEnergies',
  'E.Leclerc': 'Leclerc',
  'Leclerc': 'Leclerc',
  'E. Leclerc': 'Leclerc',
  'LECLERC': 'Leclerc',
  'Carrefour': 'Carrefour',
  'Carrefour Market': 'Carrefour',
  'Carrefour Contact': 'Carrefour',
  'Carrefour Express': 'Carrefour',
  'CARREFOUR': 'Carrefour',
  'Intermarché': 'Intermarché',
  'Intermarche': 'Intermarché',
  'INTERMARCHE': 'Intermarché',
  'Intermarché Super': 'Intermarché',
  'Intermarché Contact': 'Intermarché',
  'Super U': 'Système U',
  'Hyper U': 'Système U',
  'U Express': 'Système U',
  'Système U': 'Système U',
  'Systeme U': 'Système U',
  'BP': 'BP',
  'bp': 'BP',
  'Shell': 'Shell',
  'SHELL': 'Shell',
  'Esso': 'Esso',
  'ESSO': 'Esso',
  'Esso Express': 'Esso',
  'Auchan': 'Auchan',
  'AUCHAN': 'Auchan',
  'Casino': 'Casino',
  'CASINO': 'Casino',
  'Géant Casino': 'Casino',
  'Netto': 'Netto',
  'NETTO': 'Netto',
  'Avia': 'Avia',
  'AVIA': 'Avia',
  'Dyneff': 'Dyneff',
  'DYNEFF': 'Dyneff',
  'Elan': 'Elan',
  'ELAN': 'Elan',
  'Vito': 'Vito',
  'VITO': 'Vito',
  'Cora': 'Cora',
  'CORA': 'Cora',
  'Lidl': 'Lidl',
  'LIDL': 'Lidl',
  'Colruyt': 'Colruyt',
  'COLRUYT': 'Colruyt',
};

function normalizeBrand(raw) {
  return BRAND_NORMALIZE[raw] ?? raw;
}

/**
 * Fetches fuel station brands from OpenStreetMap via Overpass API.
 * Uses ref:FR:prix-carburants tag for join with gouv.fr station IDs.
 * Returns Map<stationId, normalizedBrand>.
 */
async function fetchOSMBrands() {
  const query = `
    [out:json][timeout:180];
    (
      node["amenity"="fuel"]["ref:FR:prix-carburants"](41.0,-5.5,51.5,10.0);
      way["amenity"="fuel"]["ref:FR:prix-carburants"](41.0,-5.5,51.5,10.0);
    );
    out tags;
  `;

  const maxAttempts = 2;
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    try {
      console.log(`Fetching OSM brands (attempt ${attempt}/${maxAttempts})...`);
      const res = await fetch(OVERPASS_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: `data=${encodeURIComponent(query)}`,
        signal: AbortSignal.timeout(180_000),
      });

      if (!res.ok) throw new Error(`Overpass HTTP ${res.status}`);

      const data = await res.json();
      const brandMap = new Map();

      for (const el of data.elements) {
        const ref = el.tags?.['ref:FR:prix-carburants'];
        const rawBrand = el.tags?.brand || el.tags?.operator;
        if (ref && rawBrand) {
          const stationId = parseInt(ref, 10);
          if (!isNaN(stationId)) {
            brandMap.set(stationId, normalizeBrand(rawBrand));
          }
        }
      }

      console.log(`OSM brands: ${brandMap.size} stations matched.`);
      return brandMap;
    } catch (err) {
      console.warn(`OSM fetch attempt ${attempt} failed: ${err.message}`);
      if (attempt < maxAttempts) {
        const delay = attempt * 5000;
        console.log(`Retrying in ${delay / 1000}s...`);
        await new Promise(r => setTimeout(r, delay));
      }
    }
  }

  console.warn('OSM brand fetch failed entirely — continuing without brands.');
  return new Map();
}

async function downloadFile(url, dest) {
  console.log(`Downloading from ${url}...`);
  const res = await fetch(url);
  if (!res.ok) throw new Error(`HTTP ${res.status}: ${res.statusText}`);

  const fileStream = createWriteStream(dest);
  // @ts-ignore - ReadableStream to Node stream
  await pipeline(res.body, fileStream);
  console.log(`Downloaded to ${dest}`);
}

async function unzipFile(zipPath, outPath) {
  console.log('Unzipping...');
  const { exec } = await import('child_process');
  const { promisify } = await import('util');
  const execAsync = promisify(exec);

  try {
    await execAsync(`unzip -o -p "${zipPath}" > "${outPath}"`);
  } catch {
    // Fallback: try using Node.js built-in
    const AdmZip = await import('adm-zip').catch(() => null);
    if (AdmZip) {
      const zip = new AdmZip.default(zipPath);
      const entries = zip.getEntries();
      const xmlEntry = entries.find(e => e.entryName.endsWith('.xml'));
      if (xmlEntry) {
        writeFileSync(outPath, xmlEntry.getData());
      }
    } else {
      throw new Error('Cannot unzip file. Install adm-zip or ensure unzip is available.');
    }
  }
  console.log('Unzipped.');
}

function parseXML(xmlPath) {
  console.log('Parsing XML...');
  const stations = new Map();

  return new Promise((resolve, reject) => {
    const stream = createReadStream(xmlPath, { encoding: 'latin1' });
    let buffer = '';
    let currentStation = null;

    const rl = createInterface({ input: stream, crlfDelay: Infinity });

    rl.on('line', (line) => {
      buffer += line + '\n';

      // Detect station opening tag
      const pdvMatch = line.match(/<pdv\s+id="(\d+)"\s+latitude="([^"]*?)"\s+longitude="([^"]*?)"\s+cp="([^"]*?)"\s+pop="([^"]*?)"/);
      if (pdvMatch) {
        const [, id, latRaw, lngRaw, cp] = pdvMatch;
        currentStation = {
          id: parseInt(id),
          lat: parseFloat(latRaw) / 100000,
          lng: parseFloat(lngRaw) / 100000,
          cp: cp.padStart(5, '0'),
          addr: '',
          city: '',
          fuels: {},
        };
      }

      // Address
      if (currentStation) {
        const addrMatch = line.match(/<adresse>(.*?)<\/adresse>/i);
        if (addrMatch) {
          currentStation.addr = decodeXMLEntities(addrMatch[1].trim());
        }

        const cityMatch = line.match(/<ville>(.*?)<\/ville>/i);
        if (cityMatch) {
          currentStation.city = decodeXMLEntities(cityMatch[1].trim());
        }

        // 24/7 automate attribute on <horaires>
        const horairesMatch = line.match(/<horaires\s[^>]*automate-24-24="(\d?)"/i);
        if (horairesMatch && horairesMatch[1] === '1') {
          currentStation.h24 = true;
        }

        // Services (one per line)
        const serviceMatch = line.match(/<service>(.*?)<\/service>/i);
        if (serviceMatch) {
          if (!currentStation.services) currentStation.services = [];
          currentStation.services.push(decodeXMLEntities(serviceMatch[1].trim()));
        }

        // Fuel prices
        const priceMatch = line.match(/<prix\s+nom="([^"]*?)"\s+id="(\d+)"\s+maj="([^"]*?)"\s+valeur="([^"]*?)"/);
        if (priceMatch) {
          const [, , fuelId, maj, valeur] = priceMatch;
          const fuelName = FUEL_MAP[parseInt(fuelId)];
          if (fuelName) {
            currentStation.fuels[fuelName] = {
              p: parseFloat(valeur),
              d: formatDate(maj),
            };
          }
        }

        // Station closing tag
        if (line.includes('</pdv>')) {
          if (currentStation.lat && currentStation.lng && Object.keys(currentStation.fuels).length > 0) {
            stations.set(currentStation.id, currentStation);
          }
          currentStation = null;
        }
      }
    });

    rl.on('close', () => {
      console.log(`Parsed ${stations.size} stations with fuel prices.`);
      resolve(stations);
    });

    rl.on('error', reject);
  });
}

function decodeXMLEntities(str) {
  return str
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'");
}

function formatDate(dateStr) {
  // Input: "2026-03-04 12:30:00" or ISO format
  try {
    const d = new Date(dateStr);
    return d.toISOString().split('T')[0];
  } catch {
    return dateStr.split(' ')[0];
  }
}

function getDepartment(cp) {
  // DOM-TOM: 97xxx → 971, 972, 973, 974, 976
  if (cp.startsWith('97')) return cp.substring(0, 3);
  // Corse: 20xxx → 2A or 2B
  if (cp.startsWith('20')) {
    const num = parseInt(cp);
    return num < 20200 ? '2A' : '2B';
  }
  return cp.substring(0, 2);
}

/**
 * Loads cached brands from brands-cache.json.
 * Returns Map<stationId, brand>.
 */
function loadBrandsCache() {
  if (!existsSync(BRANDS_CACHE)) return new Map();
  try {
    const obj = JSON.parse(readFileSync(BRANDS_CACHE, 'utf-8'));
    const map = new Map(Object.entries(obj).map(([k, v]) => [parseInt(k), v]));
    console.log(`Brands cache loaded: ${map.size} stations.`);
    return map;
  } catch {
    return new Map();
  }
}

/**
 * Saves brand map to brands-cache.json for persistence across CI runs.
 */
function saveBrandsCache(brandMap) {
  const obj = Object.fromEntries(brandMap);
  writeFileSync(BRANDS_CACHE, JSON.stringify(obj));
  console.log(`Brands cache saved: ${brandMap.size} stations.`);
}

function groupByDepartment(stations, brandMap) {
  const groups = {};
  for (const station of stations.values()) {
    const dept = getDepartment(station.cp);
    if (!groups[dept]) groups[dept] = [];
    const entry = {
      id: station.id,
      lat: station.lat,
      lng: station.lng,
      addr: station.addr,
      city: station.city,
      cp: station.cp,
      fuels: station.fuels,
    };
    const brand = brandMap.get(station.id);
    if (brand) entry.brand = brand;
    if (station.h24) entry.h24 = true;
    if (station.services && station.services.length > 0) entry.services = station.services;
    groups[dept].push(entry);
  }
  return groups;
}

async function main() {
  console.log('=== Carburants France — Data Pipeline ===\n');

  // 1. Download
  await downloadFile(SOURCE_URL, ZIP_PATH);

  // 2. Unzip
  await unzipFile(ZIP_PATH, XML_PATH);

  // 3. Parse
  const stations = await parseXML(XML_PATH);

  // 3b. Load cached brands, then fetch fresh from OSM
  const cachedBrands = loadBrandsCache();
  const osmBrands = await fetchOSMBrands();

  // Merge: OSM brands take priority, cached brands as fallback
  const mergedBrands = new Map(cachedBrands);
  for (const [id, brand] of osmBrands) {
    mergedBrands.set(id, brand);
  }

  // Save merged brands for next run
  saveBrandsCache(mergedBrands);

  // 4. Group by department
  const groups = groupByDepartment(stations, mergedBrands);

  // 5. Write JSON files
  mkdirSync(DEPT_DIR, { recursive: true });

  let totalStations = 0;
  for (const [dept, deptStations] of Object.entries(groups)) {
    const filePath = join(DEPT_DIR, `${dept}.json`);
    writeFileSync(filePath, JSON.stringify(deptStations));
    totalStations += deptStations.length;
    console.log(`  ${dept}.json → ${deptStations.length} stations`);
  }

  // 6. Write meta.json
  const meta = { lastUpdate: new Date().toISOString() };
  writeFileSync(join(DATA_DIR, 'meta.json'), JSON.stringify(meta));

  console.log(`\nDone! ${totalStations} stations in ${Object.keys(groups).length} departments.`);

  // 7. Cleanup temp files
  try { unlinkSync(ZIP_PATH); } catch {}
  try { unlinkSync(XML_PATH); } catch {}
}

main().catch((err) => {
  console.error('FATAL:', err);
  process.exit(1);
});
