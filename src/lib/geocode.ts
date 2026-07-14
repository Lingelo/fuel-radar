import type { Coords } from '../types';

interface Feature {
  geometry: { coordinates: [number, number] };
  properties: {
    label: string;
    name: string;
    postcode: string;
    city: string;
    citycode: string;
    context: string;
  };
}

export interface AddressResult {
  label: string;
  city: string;
  postcode: string;
  lat: number;
  lng: number;
}

interface PhotonFeature {
  geometry: { coordinates: [number, number] };
  properties: {
    name?: string;
    city?: string;
    postcode?: string;
    countrycode?: string;
    state?: string;
    country?: string;
  };
}

/** Countries covered by the Spanish/Portuguese datasets. */
const PHOTON_COUNTRIES = new Set(['ES', 'PT']);

function photonToResult(f: PhotonFeature): AddressResult | null {
  const p = f.properties ?? {};
  const city = p.city ?? p.name ?? '';
  if (!city) return null;
  return {
    label: [p.name, p.state, p.country].filter(Boolean).join(', '),
    city,
    postcode: p.postcode ?? '',
    lng: f.geometry.coordinates[0],
    lat: f.geometry.coordinates[1],
  };
}

/** Forward-geocode Spanish/Portuguese places via photon.komoot.io (free, no key). */
async function searchIberia(query: string, limit = 4): Promise<AddressResult[]> {
  try {
    const url = `https://photon.komoot.io/api/?q=${encodeURIComponent(query)}&limit=${limit * 3}&lang=fr`;
    const res = await fetch(url);
    if (!res.ok) return [];
    const data: { features: PhotonFeature[] } = await res.json();
    const out: AddressResult[] = [];
    const seen = new Set<string>();
    for (const f of data.features ?? []) {
      if (!PHOTON_COUNTRIES.has((f.properties.countrycode ?? '').toUpperCase())) continue;
      const r = photonToResult(f);
      if (!r) continue;
      // Photon often returns several nearly identical entries for one place.
      const key = `${r.label}|${r.city}|${r.postcode}`;
      if (seen.has(key)) continue;
      seen.add(key);
      out.push(r);
      if (out.length >= limit) break;
    }
    return out;
  } catch {
    return [];
  }
}

/** Forward-geocode French addresses via api-adresse.data.gouv.fr (free, no key). */
async function searchFrance(query: string, limit = 8): Promise<AddressResult[]> {
  try {
    const url = `https://api-adresse.data.gouv.fr/search/?q=${encodeURIComponent(query)}&limit=${limit}`;
    const res = await fetch(url);
    if (!res.ok) return [];
    const data: { features: Feature[] } = await res.json();
    return data.features.map((f) => ({
      label: f.properties.label,
      city: f.properties.city,
      postcode: f.properties.postcode,
      lng: f.geometry.coordinates[0],
      lat: f.geometry.coordinates[1],
    }));
  } catch {
    return [];
  }
}

/**
 * Forward-geocode: French results first (BAN), then Spanish/Portuguese
 * matches (photon) so cities like "Lisboa" or "Madrid" are reachable.
 */
export async function searchAddress(query: string, limit = 8): Promise<AddressResult[]> {
  const q = query.trim();
  if (q.length < 2) return [];
  const [fr, iberia] = await Promise.all([searchFrance(q, limit), searchIberia(q)]);
  return [...fr, ...iberia];
}

/** Reverse-geocode lat/lng to a postal code + city (France, then ES/PT fallback). */
export async function reverseGeocode(coords: Coords): Promise<AddressResult | null> {
  try {
    const url = `https://api-adresse.data.gouv.fr/reverse/?lat=${coords.lat}&lon=${coords.lng}&limit=1`;
    const res = await fetch(url);
    if (res.ok) {
      const data: { features: Feature[] } = await res.json();
      const f = data.features[0];
      if (f) {
        return {
          label: f.properties.label,
          city: f.properties.city,
          postcode: f.properties.postcode,
          lng: f.geometry.coordinates[0],
          lat: f.geometry.coordinates[1],
        };
      }
    }
  } catch {
    // fall through to photon
  }
  // Outside France (or BAN down) — try photon, which covers Spain/Portugal.
  try {
    const url = `https://photon.komoot.io/reverse?lat=${coords.lat}&lon=${coords.lng}&lang=fr`;
    const res = await fetch(url);
    if (!res.ok) return null;
    const data: { features: PhotonFeature[] } = await res.json();
    const f = data.features?.[0];
    return f ? photonToResult(f) : null;
  } catch {
    return null;
  }
}

/** Reverse-geocode coords to a SearchBar-friendly label, with lat/lng fallback. */
export async function reverseGeocodeLabel(coords: Coords): Promise<string> {
  const addr = await reverseGeocode(coords);
  return addr
    ? [addr.postcode, addr.city].filter(Boolean).join(' ')
    : `${coords.lat.toFixed(3)}, ${coords.lng.toFixed(3)}`;
}

export type LocationResult =
  | { coords: Coords; denied: false }
  | { coords: null; denied: boolean };

/** Browser geolocation as a Promise. Surfaces whether the user denied access. */
export function getBrowserLocation(): Promise<LocationResult> {
  return new Promise((resolve) => {
    if (!('geolocation' in navigator)) return resolve({ coords: null, denied: false });
    navigator.geolocation.getCurrentPosition(
      (pos) =>
        resolve({
          coords: { lat: pos.coords.latitude, lng: pos.coords.longitude },
          denied: false,
        }),
      (err) =>
        resolve({
          coords: null,
          denied: err.code === err.PERMISSION_DENIED,
        }),
      { enableHighAccuracy: false, maximumAge: 5 * 60 * 1000, timeout: 10_000 },
    );
  });
}
