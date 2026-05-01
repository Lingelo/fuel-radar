import { useEffect, useState } from 'react';
import { useFavorites } from '../state/FavoritesContext';
import { useFilters } from '../state/FiltersContext';
import { useViewNav } from '../state/ViewContext';
import { fetchDepartment } from '../lib/data';
import { getDepartment } from '../lib/department';
import { haversineKm } from '../lib/distance';
import { useNearbyStations } from '../hooks/useNearbyStations';
import { StationCard } from '../components/StationCard';
import { Icon } from '../components/Icon';
import type { Station } from '../types';

/**
 * Favorites are stored as raw station IDs in localStorage. To resolve them
 * we first try the cache populated by useNearbyStations, then fall back to
 * loading any nearby department on demand. Stations not in any loaded dept
 * are silently skipped.
 */
export function FavoritesScreen() {
  const fav = useFavorites();
  const f = useFilters();
  const nav = useViewNav();
  const { stations: nearby } = useNearbyStations();
  const [resolved, setResolved] = useState<Station[]>([]);

  useEffect(() => {
    const ids = [...fav.favorites];
    if (ids.length === 0) {
      setResolved([]);
      return;
    }
    let cancelled = false;
    (async () => {
      const fromNearby = new Map<number, Station>();
      for (const s of nearby) {
        if (fav.favorites.has(s.id)) fromNearby.set(s.id, s);
      }
      const missing = ids.filter((id) => !fromNearby.has(id));
      if (missing.length > 0 && f.userLocation) {
        // Try the user's current dept as a best-effort lookup
        const tryDepts = new Set<string>();
        try {
          const url = `https://api-adresse.data.gouv.fr/reverse/?lat=${f.userLocation.lat}&lon=${f.userLocation.lng}&limit=1`;
          const res = await fetch(url);
          if (res.ok) {
            const data = await res.json();
            const cp = data.features?.[0]?.properties?.postcode;
            if (cp) tryDepts.add(getDepartment(cp));
          }
        } catch {
          // ignore
        }
        for (const dept of tryDepts) {
          const list = await fetchDepartment(dept);
          for (const s of list) {
            if (fav.favorites.has(s.id)) fromNearby.set(s.id, s);
          }
        }
      }
      if (cancelled) return;
      setResolved([...fromNearby.values()]);
    })();
    return () => {
      cancelled = true;
    };
  }, [fav.favorites, nearby, f.userLocation]);

  const enriched = resolved
    .map((s) => ({
      station: s,
      distance: f.userLocation
        ? haversineKm(f.userLocation.lat, f.userLocation.lng, s.lat, s.lng)
        : 0,
    }))
    .sort((a, b) => a.distance - b.distance);

  return (
    <div className="h-full overflow-y-auto">
      <main className="max-w-3xl mx-auto px-md py-lg space-y-lg">
        <header className="flex items-center justify-between">
          <h1 className="text-headline-lg font-semibold text-on-surface">Mes favoris</h1>
          <span className="text-body-sm text-on-surface-variant">
            {fav.favorites.size} station(s)
          </span>
        </header>

        {fav.favorites.size === 0 && (
          <div className="bg-surface-container-lowest border border-surface-variant rounded-xl p-lg text-center">
            <Icon name="star" size={48} className="text-on-surface-variant mb-sm" />
            <p className="text-body-lg text-on-surface mb-1">Aucun favori pour le moment</p>
            <p className="text-body-sm text-on-surface-variant">
              Touche l'étoile sur une station pour l'ajouter ici.
            </p>
          </div>
        )}

        {fav.favorites.size > 0 && enriched.length === 0 && (
          <div className="bg-surface-container-lowest border border-surface-variant rounded-xl p-lg text-center">
            <p className="text-body-sm text-on-surface-variant">
              Chargement des favoris hors du rayon courant…
            </p>
          </div>
        )}

        <section className="flex flex-col gap-gutter">
          {enriched.map(({ station, distance }) => (
            <StationCard
              key={station.id}
              station={station}
              selectedFuel={f.selectedFuel}
              distanceKm={distance}
              onClick={() => nav.goDetails(station.id)}
              onViewMap={() => nav.goMap(station.id)}
            />
          ))}
        </section>
      </main>
    </div>
  );
}
