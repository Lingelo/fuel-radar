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

/** Forward-geocode via api-adresse.data.gouv.fr (free, no key). */
export async function searchAddress(query: string, limit = 8): Promise<AddressResult[]> {
  const q = query.trim();
  if (q.length < 2) return [];
  const url = `https://api-adresse.data.gouv.fr/search/?q=${encodeURIComponent(q)}&limit=${limit}`;
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
}

/** Reverse-geocode lat/lng to a postal code + city. */
export async function reverseGeocode(coords: Coords): Promise<AddressResult | null> {
  const url = `https://api-adresse.data.gouv.fr/reverse/?lat=${coords.lat}&lon=${coords.lng}&limit=1`;
  const res = await fetch(url);
  if (!res.ok) return null;
  const data: { features: Feature[] } = await res.json();
  const f = data.features[0];
  if (!f) return null;
  return {
    label: f.properties.label,
    city: f.properties.city,
    postcode: f.properties.postcode,
    lng: f.geometry.coordinates[0],
    lat: f.geometry.coordinates[1],
  };
}

/** Browser geolocation as a Promise. Falls back silently. */
export function getBrowserLocation(): Promise<Coords | null> {
  return new Promise((resolve) => {
    if (!('geolocation' in navigator)) return resolve(null);
    navigator.geolocation.getCurrentPosition(
      (pos) => resolve({ lat: pos.coords.latitude, lng: pos.coords.longitude }),
      () => resolve(null),
      { enableHighAccuracy: false, maximumAge: 5 * 60 * 1000, timeout: 10_000 },
    );
  });
}
