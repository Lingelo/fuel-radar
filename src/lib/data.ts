import type { MetaData, Station, StationHistoryData } from '../types';

const BASE = import.meta.env.BASE_URL;

const deptCache = new Map<string, Station[]>();
const historyCache = new Map<string, StationHistoryData>();
let metaPromise: Promise<MetaData | null> | null = null;

/**
 * Drop in-memory station caches and best-effort purge the corresponding
 * Cache Storage entry so the next fetch goes back to the network. Used
 * when the app is brought back to foreground after a long pause.
 */
export async function invalidateStations(): Promise<void> {
  deptCache.clear();
  metaPromise = null;
  if ('caches' in window) {
    try {
      await caches.delete('station-data');
    } catch {
      // ignore — cache may not exist on first launch
    }
  }
}

export async function fetchDepartment(dept: string): Promise<Station[]> {
  if (deptCache.has(dept)) return deptCache.get(dept)!;
  try {
    const res = await fetch(`${BASE}data/departments/${dept}.json`);
    if (!res.ok) {
      deptCache.set(dept, []);
      return [];
    }
    const ct = res.headers.get('content-type') ?? '';
    if (!ct.includes('json')) {
      deptCache.set(dept, []);
      return [];
    }
    const data = (await res.json()) as Station[];
    deptCache.set(dept, data);
    return data;
  } catch {
    deptCache.set(dept, []);
    return [];
  }
}

export async function fetchDepartments(depts: string[]): Promise<Station[]> {
  const results = await Promise.all(depts.map(fetchDepartment));
  return results.flat();
}

export async function fetchMeta(): Promise<MetaData | null> {
  if (!metaPromise) {
    metaPromise = fetch(`${BASE}data/meta.json`)
      .then((r) => (r.ok ? r.json() : null))
      .catch(() => null);
  }
  return metaPromise;
}

export interface NationalHistory {
  fuels: Record<string, [number, number][]>;
  updated?: string;
}

let nationalPromise: Promise<NationalHistory | null> | null = null;
export async function fetchNationalHistory(): Promise<NationalHistory | null> {
  if (!nationalPromise) {
    nationalPromise = (async () => {
      try {
        const res = await fetch(`${BASE}data/history.json`);
        const ct = res.headers.get('content-type') ?? '';
        if (!res.ok || !ct.includes('json')) return null;
        return (await res.json()) as NationalHistory;
      } catch {
        return null;
      }
    })();
  }
  return nationalPromise;
}

/** Returns true if a fuel price's last update is older than thresholdHours. */
export function isStale(updateDate: string, thresholdHours = 72): boolean {
  const t = new Date(updateDate).getTime();
  if (Number.isNaN(t)) return false;
  return Date.now() - t > thresholdHours * 60 * 60 * 1000;
}

/** Fetch per-station price history for a whole department. Returns {} if missing or non-JSON. */
export async function fetchDeptHistory(dept: string): Promise<StationHistoryData> {
  if (historyCache.has(dept)) return historyCache.get(dept)!;
  try {
    const res = await fetch(`${BASE}data/history/${dept}.json`);
    if (!res.ok) {
      historyCache.set(dept, {});
      return {};
    }
    const ct = res.headers.get('content-type') ?? '';
    if (!ct.includes('json')) {
      historyCache.set(dept, {});
      return {};
    }
    const data = (await res.json()) as StationHistoryData;
    historyCache.set(dept, data);
    return data;
  } catch {
    historyCache.set(dept, {});
    return {};
  }
}

export function timeAgo(isoOrYmd: string): string {
  const d = new Date(isoOrYmd);
  if (Number.isNaN(d.getTime())) return isoOrYmd;
  const diffMs = Date.now() - d.getTime();
  const mins = Math.round(diffMs / 60000);
  if (mins < 1) return "à l'instant";
  if (mins < 60) return `il y a ${mins} min`;
  const hours = Math.round(mins / 60);
  if (hours < 24) return `il y a ${hours} h`;
  const days = Math.round(hours / 24);
  if (days < 7) return `il y a ${days} j`;
  return d.toLocaleDateString('fr-FR');
}
