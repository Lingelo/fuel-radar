import { activeLocaleTag, translate } from '../i18n';
import type { MetaData, Station, StationHistoryData } from '../types';

const BASE = import.meta.env.BASE_URL;

/*
 * In-memory caches are tagged with a generation. Invalidating bumps the
 * generation instead of clearing: the next read goes back to the network
 * (the service worker is NetworkFirst, so it only falls back to its cache
 * when offline), and if that fails we keep serving the last good copy
 * rather than blanking the screen.
 *
 * `cache: 'no-cache'` makes the browser revalidate with GitHub Pages
 * (cheap 304) instead of trusting its HTTP cache for up to 10 minutes.
 */
interface Entry<T> {
  gen: number;
  data: T;
}
let stationGen = 0;
let historyGen = 0;
const deptCache = new Map<string, Entry<Station[]>>();
const historyCache = new Map<string, Entry<StationHistoryData>>();
let metaPromise: Promise<MetaData | null> | null = null;
let lastMeta: MetaData | null = null;

const NO_CACHE: RequestInit = { cache: 'no-cache' };

/** Mark station data stale so the next read re-fetches it. */
export function invalidateStations(): void {
  stationGen += 1;
  metaPromise = null;
}

/** Mark history data stale so the next read re-fetches it. */
export function invalidateHistory(): void {
  historyGen += 1;
  nationalPromise = null;
  countriesPromise = null;
}

/**
 * Fetch a JSON data file. Returns `null` when the file legitimately does
 * not exist (404, or an HTML error page), throws on network failure so
 * callers can fall back to what they already have.
 */
async function fetchJson<T>(path: string): Promise<T | null> {
  const res = await fetch(`${BASE}data/${path}`, NO_CACHE);
  if (!res.ok) return null;
  const ct = res.headers.get('content-type') ?? '';
  if (!ct.includes('json')) return null;
  return (await res.json()) as T;
}

export async function fetchDepartment(dept: string): Promise<Station[]> {
  const hit = deptCache.get(dept);
  if (hit && hit.gen === stationGen) return hit.data;
  try {
    const data = (await fetchJson<Station[]>(`departments/${dept}.json`)) ?? [];
    deptCache.set(dept, { gen: stationGen, data });
    return data;
  } catch {
    // Offline / flaky network: keep the previous copy, retry on next read.
    return hit?.data ?? [];
  }
}

export async function fetchDepartments(depts: string[]): Promise<Station[]> {
  const results = await Promise.all(depts.map(fetchDepartment));
  return results.flat();
}

export async function fetchMeta(): Promise<MetaData | null> {
  if (!metaPromise) {
    metaPromise = fetchJson<MetaData>('meta.json')
      .then((m) => (lastMeta = m ?? lastMeta))
      .catch(() => {
        metaPromise = null;
        return lastMeta;
      });
  }
  return metaPromise;
}

export interface NationalHistory {
  fuels: Record<string, [number, number][]>;
  updated?: string;
}

let nationalPromise: Promise<NationalHistory | null> | null = null;
let lastNational: NationalHistory | null = null;
export async function fetchNationalHistory(): Promise<NationalHistory | null> {
  if (!nationalPromise) {
    nationalPromise = fetchJson<NationalHistory>('history.json')
      .then((d) => (lastNational = d ?? lastNational))
      .catch(() => {
        nationalPromise = null;
        return lastNational;
      });
  }
  return nationalPromise;
}

export type TrendScope = 'ALL' | 'FR' | 'ES' | 'PT';

/**
 * Daily national averages accumulated per country ('ALL' = the three
 * countries together) by scripts/generate-history-countries.mjs. Unlike the
 * French history.json there is no yearly archive behind it, so young series
 * may only hold a handful of points.
 */
export interface CountriesHistory {
  countries: Partial<Record<TrendScope, Record<string, [number, number][]>>>;
  updated?: string;
}

let countriesPromise: Promise<CountriesHistory | null> | null = null;
let lastCountries: CountriesHistory | null = null;
export async function fetchCountriesHistory(): Promise<CountriesHistory | null> {
  if (!countriesPromise) {
    countriesPromise = fetchJson<CountriesHistory>('history-countries.json')
      .then((d) => (lastCountries = d ?? lastCountries))
      .catch(() => {
        countriesPromise = null;
        return lastCountries;
      });
  }
  return countriesPromise;
}

/** Returns true if a fuel price's last update is older than thresholdHours. */
export function isStale(updateDate: string, thresholdHours = 72): boolean {
  const t = new Date(updateDate).getTime();
  if (Number.isNaN(t)) return false;
  return Date.now() - t > thresholdHours * 60 * 60 * 1000;
}

/** Fetch per-station price history for a whole department. Returns {} if missing or non-JSON. */
export async function fetchDeptHistory(dept: string): Promise<StationHistoryData> {
  const hit = historyCache.get(dept);
  if (hit && hit.gen === historyGen) return hit.data;
  try {
    const data = (await fetchJson<StationHistoryData>(`history/${dept}.json`)) ?? {};
    historyCache.set(dept, { gen: historyGen, data });
    return data;
  } catch {
    return hit?.data ?? {};
  }
}

export function timeAgo(isoOrYmd: string): string {
  const d = new Date(isoOrYmd);
  if (Number.isNaN(d.getTime())) return isoOrYmd;
  const diffMs = Date.now() - d.getTime();
  const mins = Math.round(diffMs / 60000);
  if (mins < 1) return translate('time.now');
  if (mins < 60) return translate('time.minAgo', { n: mins });
  const hours = Math.round(mins / 60);
  if (hours < 24) return translate('time.hoursAgo', { n: hours });
  const days = Math.round(hours / 24);
  if (days < 7) return translate('time.daysAgo', { n: days });
  return d.toLocaleDateString(activeLocaleTag());
}
