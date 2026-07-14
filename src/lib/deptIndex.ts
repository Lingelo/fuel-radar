import { boundingBox } from './department';

const BASE = import.meta.env.BASE_URL;

/** [minLat, maxLat, minLng, maxLng] per department code. */
export type DeptBbox = Record<string, [number, number, number, number]>;

let promise: Promise<DeptBbox | null> | null = null;

export async function fetchDeptBbox(): Promise<DeptBbox | null> {
  if (!promise) {
    promise = (async () => {
      try {
        const res = await fetch(`${BASE}data/dept-bbox.json`);
        if (!res.ok) return null;
        const ct = res.headers.get('content-type') ?? '';
        if (!ct.includes('json')) return null;
        return (await res.json()) as DeptBbox;
      } catch {
        return null;
      }
    })();
  }
  return promise;
}

/**
 * Dept codes (French departments + ES/PT pseudo-departments) whose
 * station bbox overlaps a circle of radiusKm around a point. Returns null
 * when the index could not be loaded (caller should fall back).
 */
export async function deptsAround(
  lat: number,
  lng: number,
  radiusKm: number,
): Promise<string[] | null> {
  const index = await fetchDeptBbox();
  if (!index) return null;
  return findOverlappingDepts(index, boundingBox(lat, lng, radiusKm));
}

/** Returns dept codes whose station bbox overlaps a target bounding box. */
export function findOverlappingDepts(
  index: DeptBbox,
  target: { minLat: number; maxLat: number; minLng: number; maxLng: number },
): string[] {
  const out: string[] = [];
  for (const [dept, bb] of Object.entries(index)) {
    const [minLat, maxLat, minLng, maxLng] = bb;
    const overlaps =
      maxLat >= target.minLat &&
      minLat <= target.maxLat &&
      maxLng >= target.minLng &&
      minLng <= target.maxLng;
    if (overlaps) out.push(dept);
  }
  return out;
}
