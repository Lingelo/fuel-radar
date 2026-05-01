import { useEffect, useState } from 'react';
import { fetchDepartments } from '../lib/data';
import { boundingBox, getDepartment } from '../lib/department';
import { haversineKm } from '../lib/distance';
import { useFilters } from '../state/FiltersContext';
import type { Station } from '../types';

interface Result {
  stations: Station[];
  loading: boolean;
}

/**
 * Loads stations within the active radius around the user location.
 * Discovers involved French departments by reverse-geocoding a 5x5 grid
 * across the bounding box. Adds neighbour-of-user department safety net.
 */
export function useNearbyStations(): Result {
  const { userLocation, radiusKm } = useFilters();
  const [stations, setStations] = useState<Station[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!userLocation) return;
    let cancelled = false;
    setLoading(true);

    (async () => {
      const expandKm = Math.max(radiusKm, 15);
      const box = boundingBox(userLocation.lat, userLocation.lng, expandKm);
      // Dense 7x7 grid (49 anchors) — required to reliably cover dense
      // areas like Île-de-France where dept boundaries are tight.
      const grid: [number, number][] = [];
      const steps = 7;
      for (let i = 0; i < steps; i++) {
        for (let j = 0; j < steps; j++) {
          const lat = box.minLat + ((box.maxLat - box.minLat) * i) / (steps - 1);
          const lng = box.minLng + ((box.maxLng - box.minLng) * j) / (steps - 1);
          grid.push([lat, lng]);
        }
      }

      const deptCodes = new Set<string>();
      await Promise.all(
        grid.map(async ([lat, lng]) => {
          try {
            const url = `https://api-adresse.data.gouv.fr/reverse/?lat=${lat}&lon=${lng}&limit=1`;
            const res = await fetch(url);
            if (!res.ok) return;
            const data = await res.json();
            const cp = data.features?.[0]?.properties?.postcode;
            if (cp) deptCodes.add(getDepartment(cp));
          } catch {
            // ignore
          }
        }),
      );

      // Always include the user's primary department in case the API
      // refused some anchors (the central anchor sometimes fails).
      try {
        const r = await fetch(
          `https://api-adresse.data.gouv.fr/reverse/?lat=${userLocation.lat}&lon=${userLocation.lng}&limit=1`,
        );
        if (r.ok) {
          const d = await r.json();
          const cp = d.features?.[0]?.properties?.postcode;
          if (cp) deptCodes.add(getDepartment(cp));
        }
      } catch {
        // ignore
      }

      if (cancelled) return;
      if (deptCodes.size === 0) {
        setLoading(false);
        return;
      }

      const all = await fetchDepartments(Array.from(deptCodes));
      if (cancelled) return;

      const within = all.filter((s) => {
        const d = haversineKm(userLocation.lat, userLocation.lng, s.lat, s.lng);
        return d <= radiusKm;
      });

      setStations(within);
      setLoading(false);
    })().catch(() => {
      if (!cancelled) setLoading(false);
    });

    return () => {
      cancelled = true;
    };
  }, [userLocation, radiusKm]);

  return { stations, loading };
}
