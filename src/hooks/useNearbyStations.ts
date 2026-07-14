import { useEffect, useState } from 'react';
import { fetchDepartments } from '../lib/data';
import { getDepartment } from '../lib/department';
import { deptsAround } from '../lib/deptIndex';
import { haversineKm } from '../lib/distance';
import { useFilters } from '../state/FiltersContext';
import { useForegroundRefresh } from './useForegroundRefresh';
import type { Station } from '../types';

interface Result {
  stations: Station[];
  loading: boolean;
}

/**
 * Loads stations within the active radius around the user location.
 * Discovers involved data files (French departments, Spanish provinces,
 * Portuguese districts) via the dept-bbox.json index; falls back to
 * reverse-geocoding the user position (France only) if the index is missing.
 */
export function useNearbyStations(): Result {
  const { userLocation, radiusKm } = useFilters();
  // Bumped when the app returns to foreground after >60s — re-runs the
  // effect below so the user always sees fresh prices on app resume.
  const foregroundVersion = useForegroundRefresh();
  const [stations, setStations] = useState<Station[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!userLocation) return;
    let cancelled = false;
    setLoading(true);

    (async () => {
      const expandKm = Math.max(radiusKm, 15);
      const deptCodes = new Set<string>(
        (await deptsAround(userLocation.lat, userLocation.lng, expandKm)) ?? [],
      );

      // Index unavailable (e.g. first offline launch) — fall back to
      // reverse-geocoding the user position. France only.
      if (deptCodes.size === 0) {
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
  }, [userLocation, radiusKm, foregroundVersion]);

  return { stations, loading };
}
