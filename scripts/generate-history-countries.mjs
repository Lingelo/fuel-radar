#!/usr/bin/env node

/**
 * Accumulates daily national average fuel prices per country into
 * public/data/history-countries.json, from the department snapshot files
 * written by process-data.mjs (FR) and process-data-iberia.mjs (ES/PT).
 *
 * Unlike the French pipeline (generate-history.mjs), Spain and Portugal have
 * no downloadable yearly archive, so their history cannot be reconstructed —
 * it has to be accumulated over time. This file is therefore committed back
 * to the repository by the update-data workflow (like brands-cache.json) so
 * each scheduled run extends the series by at most one point per day.
 *
 * Output format:
 *   {
 *     countries: {
 *       FR:  { Gazole: [[epoch, avgPrice], ...], ... },
 *       ES:  { ... },
 *       PT:  { ... },
 *       ALL: { ... }   // every station of the three countries together
 *     },
 *     updated: ISO
 *   }
 *
 * Run after process-data.mjs and process-data-iberia.mjs.
 */

import { writeFileSync, existsSync, readFileSync, readdirSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const ROOT = join(__dirname, '..');
const DEPT_DIR = join(ROOT, 'public', 'data', 'departments');
const OUT_PATH = join(ROOT, 'public', 'data', 'history-countries.json');

const FUELS = ['Gazole', 'SP95', 'E10', 'SP98', 'E85', 'GPLc'];
const SCOPES = ['FR', 'ES', 'PT', 'ALL'];

// Same bounds as generate-history.mjs: enough stations for a meaningful
// average, and a rolling window so the file stays small forever.
const MIN_PRICES_PER_POINT = 10;
const RETENTION_DAYS = 730;

function countryOf(deptFile) {
  if (deptFile.startsWith('ES-')) return 'ES';
  if (deptFile.startsWith('PT-')) return 'PT';
  return 'FR';
}

/** scope -> fuel -> { prices: number[], latestDate: 'YYYY-MM-DD' } */
function collectPrices() {
  const acc = new Map();
  for (const scope of SCOPES) {
    acc.set(scope, new Map(FUELS.map((f) => [f, { prices: [], latestDate: '' }])));
  }

  const files = readdirSync(DEPT_DIR).filter((f) => f.endsWith('.json'));
  let stationCount = 0;
  for (const file of files) {
    const country = countryOf(file);
    let stations;
    try {
      stations = JSON.parse(readFileSync(join(DEPT_DIR, file), 'utf-8'));
    } catch {
      console.warn(`  skipping unreadable ${file}`);
      continue;
    }
    if (!Array.isArray(stations)) continue;
    stationCount += stations.length;

    for (const station of stations) {
      for (const fuel of FUELS) {
        const fp = station.fuels?.[fuel];
        if (!fp || typeof fp.p !== 'number' || fp.p <= 0 || fp.p > 5) continue;
        for (const scope of [country, 'ALL']) {
          const bucket = acc.get(scope).get(fuel);
          bucket.prices.push(fp.p);
          if (typeof fp.d === 'string' && fp.d > bucket.latestDate) bucket.latestDate = fp.d;
        }
      }
    }
  }

  console.log(`Scanned ${files.length} department files, ${stationCount} stations.`);
  return acc;
}

function loadExisting() {
  if (!existsSync(OUT_PATH)) return { countries: {} };
  try {
    const parsed = JSON.parse(readFileSync(OUT_PATH, 'utf-8'));
    if (parsed && typeof parsed === 'object' && parsed.countries) return parsed;
  } catch {}
  return { countries: {} };
}

function main() {
  console.log('=== FuelRadar — per-country daily averages ===\n');
  const acc = collectPrices();
  const history = loadExisting();

  const pruneCutoff = Date.now() - RETENTION_DAYS * 86400_000;
  let added = 0;
  for (const scope of SCOPES) {
    if (!history.countries[scope]) history.countries[scope] = {};
    for (const fuel of FUELS) {
      const { prices, latestDate } = acc.get(scope).get(fuel);
      const series = (history.countries[scope][fuel] ?? []).filter(([e]) => e >= pruneCutoff);
      history.countries[scope][fuel] = series;

      if (prices.length < MIN_PRICES_PER_POINT || !latestDate) continue;
      // One point per calendar day, stamped with the freshest update date in
      // the snapshot (repo data may be older than the wall clock). First run
      // of the day wins; later same-day runs are no-ops to keep commits calm.
      const epoch = new Date(latestDate + 'T12:00:00Z').getTime();
      if (Number.isNaN(epoch) || series.some(([e]) => e === epoch)) continue;

      const avg = prices.reduce((s, p) => s + p, 0) / prices.length;
      series.push([epoch, Math.round(avg * 1000) / 1000]);
      series.sort((a, b) => a[0] - b[0]);
      added++;
      console.log(`  ${scope} ${fuel}: ${latestDate} → ${avg.toFixed(3)} € (${prices.length} stations)`);
    }
  }

  if (added === 0 && existsSync(OUT_PATH)) {
    // Leave the file byte-identical so the workflow's "commit if changed"
    // step stays quiet on the 2-hourly runs after the day's first one.
    console.log('\nNo new points for today — file left untouched.');
    return;
  }

  history.updated = new Date().toISOString();
  writeFileSync(OUT_PATH, JSON.stringify(history));
  const sizeKB = (Buffer.byteLength(JSON.stringify(history)) / 1024).toFixed(1);
  console.log(`\nAdded ${added} points. Saved history-countries.json (${sizeKB} KB).`);
}

main();
