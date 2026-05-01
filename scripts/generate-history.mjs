#!/usr/bin/env node

/**
 * Generates fuel price history data:
 * 1. National daily averages → public/data/history.json
 * 2. Per-station history by department → public/data/history/{dept}.json
 *
 * Modes:
 *   --bootstrap   Download the full 2026 annual archive and compute all history
 *   --daily       Download yesterday's daily flux and append new data points
 *
 * Output formats:
 *   history.json: { fuels: { Gazole: [[epoch, price], ...] }, updated: ISO }
 *   history/{dept}.json: { "stationId": { "Gazole": [[epoch, price], ...] } }
 */

import { writeFileSync, mkdirSync, existsSync, readFileSync, unlinkSync, createWriteStream } from 'fs';
import { createReadStream } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';
import { createInterface } from 'readline';

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = join(__dirname, '..');
const DATA_DIR = join(ROOT, 'public', 'data');
const HISTORY_PATH = join(DATA_DIR, 'history.json');
const STATION_HISTORY_DIR = join(DATA_DIR, 'history');
const TMP_ZIP = join(ROOT, '.tmp-history.zip');
const TMP_XML = join(ROOT, '.tmp-history.xml');

const FUEL_MAP = {
  1: 'Gazole',
  2: 'SP95',
  3: 'E85',
  4: 'GPLc',
  5: 'E10',
  6: 'SP98',
};

const FUELS_TO_TRACK = ['Gazole', 'SP95', 'SP98', 'E10', 'E85'];

const ANNUAL_URL = 'https://donnees.roulez-eco.fr/opendata/annee/2026';
const DAILY_URL = 'https://donnees.roulez-eco.fr/opendata/jour';

// ─── Helpers ─────────────────────────────────────────────

function getDepartment(cp) {
  if (cp.startsWith('97')) return cp.substring(0, 3);
  if (cp.startsWith('20')) {
    const num = parseInt(cp);
    return num < 20200 ? '2A' : '2B';
  }
  return cp.substring(0, 2);
}

async function downloadFile(url, dest) {
  console.log(`Downloading ${url}...`);
  const res = await fetch(url);
  if (!res.ok) throw new Error(`HTTP ${res.status}: ${res.statusText}`);
  const fileStream = createWriteStream(dest);
  await import('stream/promises').then(m => m.pipeline(res.body, fileStream));
  console.log(`Downloaded to ${dest}`);
}

async function unzipFile(zipPath, outPath) {
  const { exec } = await import('child_process');
  const { promisify } = await import('util');
  const execAsync = promisify(exec);
  try {
    await execAsync(`unzip -o -p "${zipPath}" > "${outPath}"`);
  } catch {
    throw new Error('unzip failed. Ensure unzip is available.');
  }
}

/**
 * Stream-parse the XML and collect prices grouped by date + fuel.
 * Returns Map<dateStr, Map<fuelName, number[]>> (arrays of prices)
 */
function parseXMLForAverages(xmlPath) {
  console.log('Parsing XML for daily averages...');

  return new Promise((resolve, reject) => {
    // date -> fuel -> prices[]
    const dailyPrices = new Map();
    let lineCount = 0;

    const stream = createReadStream(xmlPath, { encoding: 'latin1' });
    const rl = createInterface({ input: stream, crlfDelay: Infinity });

    rl.on('line', (line) => {
      lineCount++;
      if (lineCount % 500_000 === 0) console.log(`  ...${lineCount} lines`);

      const priceMatch = line.match(/<prix\s+nom="[^"]*?"\s+id="(\d+)"\s+maj="([^"]*?)"\s+valeur="([^"]*?)"/);
      if (!priceMatch) return;

      const [, fuelId, majStr, valeur] = priceMatch;
      const fuelName = FUEL_MAP[parseInt(fuelId)];
      if (!fuelName || !FUELS_TO_TRACK.includes(fuelName)) return;

      const price = parseFloat(valeur);
      if (isNaN(price) || price <= 0 || price > 5) return; // sanity check

      // Extract date (YYYY-MM-DD)
      const dateStr = majStr.split(/[T ]/)[0];
      if (!dateStr || dateStr.length !== 10) return;

      if (!dailyPrices.has(dateStr)) dailyPrices.set(dateStr, new Map());
      const dayMap = dailyPrices.get(dateStr);
      if (!dayMap.has(fuelName)) dayMap.set(fuelName, []);
      dayMap.get(fuelName).push(price);
    });

    rl.on('close', () => {
      console.log(`Parsed ${lineCount} lines, ${dailyPrices.size} unique dates.`);
      resolve(dailyPrices);
    });
    rl.on('error', reject);
  });
}

/**
 * Stream-parse the XML and collect per-station prices grouped by date + fuel.
 * Returns Map<stationId, { cp: string, fuels: Map<fuelName, Map<dateStr, price>> }>
 * Keeps only the latest price per day per fuel per station.
 */
function parseXMLForStationHistory(xmlPath) {
  console.log('Parsing XML for per-station history...');

  return new Promise((resolve, reject) => {
    // stationId -> { cp, fuels: Map<fuel, Map<date, price>> }
    const stationPrices = new Map();
    let lineCount = 0;
    let currentStationId = null;
    let currentCp = null;

    const stream = createReadStream(xmlPath, { encoding: 'latin1' });
    const rl = createInterface({ input: stream, crlfDelay: Infinity });

    rl.on('line', (line) => {
      lineCount++;
      if (lineCount % 500_000 === 0) console.log(`  ...${lineCount} lines (stations)`);

      // Detect station opening tag to track current station context
      const pdvMatch = line.match(/<pdv\s+id="(\d+)"[^>]*cp="([^"]*?)"/);
      if (pdvMatch) {
        currentStationId = parseInt(pdvMatch[1]);
        currentCp = pdvMatch[2].padStart(5, '0');
        if (!stationPrices.has(currentStationId)) {
          stationPrices.set(currentStationId, { cp: currentCp, fuels: new Map() });
        }
        return;
      }

      if (line.includes('</pdv>')) {
        currentStationId = null;
        currentCp = null;
        return;
      }

      if (!currentStationId) return;

      const priceMatch = line.match(/<prix\s+nom="[^"]*?"\s+id="(\d+)"\s+maj="([^"]*?)"\s+valeur="([^"]*?)"/);
      if (!priceMatch) return;

      const [, fuelId, majStr, valeur] = priceMatch;
      const fuelName = FUEL_MAP[parseInt(fuelId)];
      if (!fuelName || !FUELS_TO_TRACK.includes(fuelName)) return;

      const price = parseFloat(valeur);
      if (isNaN(price) || price <= 0 || price > 5) return;

      const dateStr = majStr.split(/[T ]/)[0];
      if (!dateStr || dateStr.length !== 10) return;

      const stationData = stationPrices.get(currentStationId);
      if (!stationData.fuels.has(fuelName)) stationData.fuels.set(fuelName, new Map());
      // Keep latest price for each date (overwrites earlier intra-day updates)
      stationData.fuels.get(fuelName).set(dateStr, price);
    });

    rl.on('close', () => {
      console.log(`Parsed ${lineCount} lines, ${stationPrices.size} unique stations.`);
      resolve(stationPrices);
    });
    rl.on('error', reject);
  });
}

/**
 * Convert stationPrices to per-department JSON files.
 * Output: { "stationId": { "Gazole": [[epoch, price], ...], ... } }
 */
function saveStationHistory(stationPrices) {
  mkdirSync(STATION_HISTORY_DIR, { recursive: true });

  // Group by department
  const deptData = new Map();

  for (const [stationId, { cp, fuels }] of stationPrices) {
    const dept = getDepartment(cp);
    if (!deptData.has(dept)) deptData.set(dept, {});
    const deptObj = deptData.get(dept);

    const stationHistory = {};
    for (const [fuelName, datePrices] of fuels) {
      const sortedDates = [...datePrices.keys()].sort();
      stationHistory[fuelName] = sortedDates.map(dateStr => [
        new Date(dateStr + 'T12:00:00Z').getTime(),
        Math.round(datePrices.get(dateStr) * 1000) / 1000,
      ]);
    }

    // Only include stations with at least 2 data points in any fuel
    const hasHistory = Object.values(stationHistory).some(arr => arr.length >= 2);
    if (hasHistory) {
      deptObj[stationId] = stationHistory;
    }
  }

  let totalFiles = 0;
  let totalSize = 0;
  for (const [dept, data] of deptData) {
    const filePath = join(STATION_HISTORY_DIR, `${dept}.json`);
    const json = JSON.stringify(data);
    writeFileSync(filePath, json);
    const sizeKB = (Buffer.byteLength(json) / 1024).toFixed(1);
    totalSize += Buffer.byteLength(json);
    totalFiles++;
    console.log(`  history/${dept}.json → ${Object.keys(data).length} stations (${sizeKB} KB)`);
  }

  console.log(`\nStation history: ${totalFiles} dept files, ${(totalSize / 1024).toFixed(0)} KB total.`);
}

/**
 * Retention window: drop history points older than this many days at every
 * merge/save. Keeps file sizes bounded so per-dept JSONs don't grow forever.
 * 730 days = 2 years, comfortably covers the "1 an" view in TrendsScreen.
 */
const RETENTION_DAYS = 730;

function pruneOldEntries(entries) {
  const cutoff = Date.now() - RETENTION_DAYS * 86400_000;
  return entries.filter(([epoch]) => epoch >= cutoff);
}

/**
 * Merge new station prices into existing per-department history files.
 */
function mergeStationHistory(stationPrices) {
  mkdirSync(STATION_HISTORY_DIR, { recursive: true });

  // Group new data by department
  const deptNew = new Map();
  for (const [stationId, { cp, fuels }] of stationPrices) {
    const dept = getDepartment(cp);
    if (!deptNew.has(dept)) deptNew.set(dept, new Map());
    deptNew.get(dept).set(stationId, fuels);
  }

  let addedTotal = 0;
  let prunedTotal = 0;
  for (const [dept, newStations] of deptNew) {
    const filePath = join(STATION_HISTORY_DIR, `${dept}.json`);
    let existing = {};
    if (existsSync(filePath)) {
      try { existing = JSON.parse(readFileSync(filePath, 'utf-8')); } catch {}
    }

    for (const [stationId, fuels] of newStations) {
      if (!existing[stationId]) existing[stationId] = {};

      for (const [fuelName, datePrices] of fuels) {
        if (!existing[stationId][fuelName]) existing[stationId][fuelName] = [];

        const existingEpochs = new Set(existing[stationId][fuelName].map(([e]) => e));
        for (const [dateStr, price] of datePrices) {
          const epoch = new Date(dateStr + 'T12:00:00Z').getTime();
          if (!existingEpochs.has(epoch)) {
            existing[stationId][fuelName].push([epoch, Math.round(price * 1000) / 1000]);
            addedTotal++;
          }
        }
        existing[stationId][fuelName].sort((a, b) => a[0] - b[0]);
        const before = existing[stationId][fuelName].length;
        existing[stationId][fuelName] = pruneOldEntries(existing[stationId][fuelName]);
        prunedTotal += before - existing[stationId][fuelName].length;
      }
    }

    writeFileSync(filePath, JSON.stringify(existing));
  }

  console.log(`Merged ${addedTotal} new station-level data points (pruned ${prunedTotal} stale points beyond ${RETENTION_DAYS} days).`);
}

/**
 * Convert dailyPrices map to the output format:
 * { fuels: { Gazole: [[epoch, avgPrice], ...], ... }, updated: ISO }
 * Sorted by date ascending.
 */
function computeAverages(dailyPrices) {
  const fuels = {};
  for (const fuel of FUELS_TO_TRACK) {
    fuels[fuel] = [];
  }

  // Sort dates
  const sortedDates = [...dailyPrices.keys()].sort();

  for (const dateStr of sortedDates) {
    const epoch = new Date(dateStr + 'T12:00:00Z').getTime();
    const dayMap = dailyPrices.get(dateStr);

    for (const fuel of FUELS_TO_TRACK) {
      const prices = dayMap.get(fuel);
      if (!prices || prices.length < 10) continue; // need enough data points
      const avg = prices.reduce((s, p) => s + p, 0) / prices.length;
      fuels[fuel].push([epoch, Math.round(avg * 1000) / 1000]);
    }
  }

  return fuels;
}

function loadExistingHistory() {
  if (!existsSync(HISTORY_PATH)) return null;
  try {
    return JSON.parse(readFileSync(HISTORY_PATH, 'utf-8'));
  } catch {
    return null;
  }
}

function saveHistory(history) {
  mkdirSync(DATA_DIR, { recursive: true });
  // Apply the same retention window to the national daily averages so the
  // top-of-app history.json stays bounded (currently ~16 KB; without
  // pruning it would cross 100 KB after a few years).
  if (history && history.fuels) {
    for (const fuel of Object.keys(history.fuels)) {
      history.fuels[fuel] = pruneOldEntries(history.fuels[fuel]);
    }
  }
  writeFileSync(HISTORY_PATH, JSON.stringify(history));
  const sizeKB = (Buffer.byteLength(JSON.stringify(history)) / 1024).toFixed(1);
  console.log(`Saved history.json (${sizeKB} KB)`);
}

function cleanup() {
  try { unlinkSync(TMP_ZIP); } catch {}
  try { unlinkSync(TMP_XML); } catch {}
}

// ─── Bootstrap: full annual archive ──────────────────────

async function bootstrap() {
  console.log('=== Bootstrap: generating history from 2026 archive ===\n');

  await downloadFile(ANNUAL_URL, TMP_ZIP);
  await unzipFile(TMP_ZIP, TMP_XML);

  // 1. National averages
  const dailyPrices = await parseXMLForAverages(TMP_XML);
  const fuels = computeAverages(dailyPrices);

  const totalPoints = Object.values(fuels).reduce((s, arr) => s + arr.length, 0);
  console.log(`\nComputed ${totalPoints} national average data points.`);

  const history = { fuels, updated: new Date().toISOString() };
  saveHistory(history);

  // 2. Per-station history
  const stationPrices = await parseXMLForStationHistory(TMP_XML);
  saveStationHistory(stationPrices);

  cleanup();
  console.log('Done!');
}

// ─── Daily: append yesterday's average ───────────────────

async function daily() {
  console.log('=== Daily: appending latest data ===\n');

  await downloadFile(DAILY_URL, TMP_ZIP);
  await unzipFile(TMP_ZIP, TMP_XML);

  const dailyPrices = await parseXMLForAverages(TMP_XML);
  const newFuels = computeAverages(dailyPrices);

  // Load existing history and merge
  const existing = loadExistingHistory();
  if (!existing) {
    console.error('No existing history.json — run with --bootstrap first.');
    cleanup();
    process.exit(1);
  }

  let added = 0;
  for (const fuel of FUELS_TO_TRACK) {
    const existingDates = new Set(existing.fuels[fuel]?.map(([e]) => e) ?? []);
    for (const [epoch, avg] of (newFuels[fuel] ?? [])) {
      if (!existingDates.has(epoch)) {
        if (!existing.fuels[fuel]) existing.fuels[fuel] = [];
        existing.fuels[fuel].push([epoch, avg]);
        added++;
      }
    }
    // Keep sorted
    existing.fuels[fuel]?.sort((a, b) => a[0] - b[0]);
  }

  existing.updated = new Date().toISOString();
  saveHistory(existing);

  // 2. Per-station history (merge new data)
  const stationPrices = await parseXMLForStationHistory(TMP_XML);
  mergeStationHistory(stationPrices);

  cleanup();
  console.log(`Added ${added} new national data points. Done!`);
}

// ─── CLI ─────────────────────────────────────────────────

const mode = process.argv[2];
if (mode === '--bootstrap') {
  bootstrap().catch(err => { console.error('FATAL:', err); process.exit(1); });
} else if (mode === '--daily') {
  daily().catch(err => { console.error('FATAL:', err); process.exit(1); });
} else {
  console.log('Usage: node scripts/generate-history.mjs [--bootstrap|--daily]');
  process.exit(1);
}
