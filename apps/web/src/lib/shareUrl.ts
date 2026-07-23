import { FUEL_TYPES, type Coords, type FuelType } from '../types';

/** Hydrated state extracted from a shared zone URL. */
export interface ZoneShareState {
  coords: Coords;
  fuel?: FuelType;
  radiusKm?: number;
  brands?: string[];
  openH24Only?: boolean;
}

/**
 * Parses `?lat=…&lng=…&fuel=…&radius=…&brands=…&h24=1` from `window.location.search`.
 * Returns `null` when no usable coordinates are present.
 *
 * The query string is preferred over the hash because the hash already
 * encodes the current view route — keeping concerns separate avoids
 * having to escape `&` inside hash fragments.
 */
export function parseZoneShare(search: string): ZoneShareState | null {
  if (!search) return null;
  const params = new URLSearchParams(search);
  const latRaw = params.get('lat');
  const lngRaw = params.get('lng');
  if (latRaw === null || lngRaw === null) return null;
  const lat = Number(latRaw);
  const lng = Number(lngRaw);
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null;
  if (lat < -90 || lat > 90 || lng < -180 || lng > 180) return null;

  const fuelParam = params.get('fuel');
  const fuel = fuelParam && (FUEL_TYPES as string[]).includes(fuelParam)
    ? (fuelParam as FuelType)
    : undefined;

  const radiusRaw = params.get('radius');
  const radius = radiusRaw !== null ? Number(radiusRaw) : NaN;
  const radiusKm = Number.isFinite(radius)
    ? Math.min(30, Math.max(1, radius))
    : undefined;

  const brandsRaw = params.get('brands');
  const brands = brandsRaw
    ? brandsRaw.split(',').map((b) => b.trim()).filter((b) => b.length > 0)
    : undefined;

  const openH24Only = params.get('h24') === '1';

  return { coords: { lat, lng }, fuel, radiusKm, brands, openH24Only };
}

interface BuildOpts {
  coords: Coords;
  fuel: FuelType;
  radiusKm: number;
  brands: string[];
  openH24Only: boolean;
}

/** Builds the absolute URL for sharing the current zone. */
export function buildZoneShareUrl(opts: BuildOpts): string {
  const params = new URLSearchParams();
  params.set('lat', opts.coords.lat.toFixed(5));
  params.set('lng', opts.coords.lng.toFixed(5));
  params.set('fuel', opts.fuel);
  params.set('radius', String(opts.radiusKm));
  if (opts.brands.length > 0) params.set('brands', opts.brands.join(','));
  if (opts.openH24Only) params.set('h24', '1');

  const { origin, pathname } = window.location;
  return `${origin}${pathname}?${params.toString()}`;
}

/**
 * Removes the zone share params from the URL after hydration so that
 * subsequent user interactions (zoom, pan, fuel change) aren't tied
 * to the shared snapshot.
 */
export function clearZoneShareFromUrl(): void {
  const url = new URL(window.location.href);
  let mutated = false;
  for (const key of ['lat', 'lng', 'fuel', 'radius', 'brands', 'h24']) {
    if (url.searchParams.has(key)) {
      url.searchParams.delete(key);
      mutated = true;
    }
  }
  if (!mutated) return;
  const search = url.searchParams.toString();
  const next = `${url.pathname}${search ? `?${search}` : ''}${url.hash}`;
  window.history.replaceState(null, '', next);
}
