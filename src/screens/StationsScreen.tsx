import { useMemo, useState } from 'react';
import { useFilters } from '../state/FiltersContext';
import { useViewNav } from '../state/ViewContext';
import { useFavorites } from '../state/FavoritesContext';
import { useNearbyStations } from '../hooks/useNearbyStations';
import { FUEL_TYPES, type FuelType } from '../types';
import { haversineKm } from '../lib/distance';
import { getPriceBounds, getPriceColor } from '../lib/priceColor';
import { FuelChip } from '../components/FuelChip';
import { StationCard } from '../components/StationCard';
import { FilterSheet } from '../components/FilterSheet';
import { Icon } from '../components/Icon';

export function StationsScreen() {
  const f = useFilters();
  const nav = useViewNav();
  const fav = useFavorites();
  const { stations, loading } = useNearbyStations();
  const [filterOpen, setFilterOpen] = useState(false);

  const sorted = useMemo(() => {
    if (!f.userLocation) return [];
    const filtered = stations
      .filter((s) => s.fuels[f.selectedFuel])
      .filter((s) => f.selectedBrands.size === 0 || (s.brand && f.selectedBrands.has(s.brand)));
    const prices = filtered.map((s) => s.fuels[f.selectedFuel]!.p);
    const { pMin, pMax } = getPriceBounds(prices);
    let list = filtered.map((s) => {
      const price = s.fuels[f.selectedFuel]!.p;
      return {
        station: s,
        price,
        distance: haversineKm(f.userLocation!.lat, f.userLocation!.lng, s.lat, s.lng),
        color: getPriceColor(price, pMin, pMax),
      };
    });
    if (f.sortBy === 'price') {
      list.sort((a, b) => a.price - b.price || a.distance - b.distance);
    } else {
      list.sort((a, b) => a.distance - b.distance);
    }
    return list;
  }, [stations, f.selectedFuel, f.selectedBrands, f.sortBy, f.userLocation]);

  return (
    <>
      <div className="h-full overflow-y-auto">
        <main className="max-w-3xl mx-auto px-md py-lg space-y-xl">
          <section className="flex flex-col gap-sm">
            <div className="flex items-center justify-between gap-sm">
              <h2 className="text-headline-lg font-semibold text-on-surface">
                Stations à proximité
              </h2>
              <div className="flex items-center gap-1 bg-surface-container-lowest rounded-lg p-1 border border-surface-variant">
                <button
                  onClick={() => f.setSortBy('price')}
                  className={[
                    'px-3 py-1 rounded text-body-sm transition-colors',
                    f.sortBy === 'price'
                      ? 'bg-surface-variant text-on-surface-variant font-medium'
                      : 'text-on-surface hover:bg-surface-container',
                  ].join(' ')}
                >
                  Prix
                </button>
                <button
                  onClick={() => f.setSortBy('distance')}
                  className={[
                    'px-3 py-1 rounded text-body-sm transition-colors',
                    f.sortBy === 'distance'
                      ? 'bg-surface-variant text-on-surface-variant font-medium'
                      : 'text-on-surface hover:bg-surface-container',
                  ].join(' ')}
                >
                  Distance
                </button>
              </div>
            </div>
            <div className="flex gap-2 overflow-x-auto pb-2 no-scrollbar">
              {FUEL_TYPES.map((fuel: FuelType) => (
                <FuelChip
                  key={fuel}
                  fuel={fuel}
                  active={f.selectedFuel === fuel}
                  onClick={() => f.setSelectedFuel(fuel)}
                />
              ))}
              <button
                onClick={() => setFilterOpen(true)}
                className="shrink-0 px-3 py-2 rounded-full bg-surface-container-lowest border border-outline-variant text-on-surface text-label-caps font-bold tracking-wider flex items-center gap-1"
              >
                <Icon name="tune" size={14} /> Filtres
              </button>
            </div>
            <p className="text-body-sm text-on-surface-variant">
              Rayon : {f.radiusKm} km {f.searchLabel ? `autour de ${f.searchLabel}` : ''}
            </p>
          </section>

          <section className="flex flex-col gap-gutter">
            {loading && sorted.length === 0 && (
              <p className="text-center text-body-sm text-on-surface-variant py-lg">
                Chargement des stations…
              </p>
            )}
            {!loading && sorted.length === 0 && (
              <p className="text-center text-body-sm text-on-surface-variant py-lg">
                Aucune station {f.selectedFuel} dans ce rayon. Ajustez les filtres.
              </p>
            )}
            {sorted.map(({ station, distance, color }, idx) => (
              <StationCard
                key={station.id}
                station={station}
                selectedFuel={f.selectedFuel}
                distanceKm={distance}
                isCheapest={idx === 0 && f.sortBy === 'price'}
                priceColor={color}
                isFavorite={fav.isFavorite(station.id)}
                onToggleFavorite={() => fav.toggle(station.id)}
                onClick={() => nav.goDetails(station.id)}
                onViewMap={() => nav.goMap(station.id)}
              />
            ))}
          </section>
        </main>
      </div>
      <FilterSheet open={filterOpen} onClose={() => setFilterOpen(false)} />
    </>
  );
}
