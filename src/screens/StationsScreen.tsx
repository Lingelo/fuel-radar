import { useMemo, useState } from 'react';
import { useFilters } from '../state/FiltersContext';
import { useViewNav } from '../state/ViewContext';
import { useFavorites } from '../state/FavoritesContext';
import { useNearbyStations } from '../hooks/useNearbyStations';
import { FUEL_TYPES, type FuelType } from '../types';
import { haversineKm } from '../lib/distance';
import { getBrowserLocation, reverseGeocodeLabel } from '../lib/geocode';
import { getPriceBounds, getPriceColor } from '../lib/priceColor';
import { FuelChip } from '../components/FuelChip';
import { StationCard } from '../components/StationCard';
import { SearchBar } from '../components/SearchBar';
import { FilterSheet } from '../components/FilterSheet';
import { Icon } from '../components/Icon';

export function StationsScreen() {
  const f = useFilters();
  const nav = useViewNav();
  const fav = useFavorites();
  const { stations, loading } = useNearbyStations();
  const [filterOpen, setFilterOpen] = useState(false);
  const [locating, setLocating] = useState(false);
  const [locationDenied, setLocationDenied] = useState(false);

  const onLocateMe = async () => {
    setLocating(true);
    try {
      const { coords, denied } = await getBrowserLocation();
      if (coords) {
        f.setUserLocation(coords);
        f.setSearchLabel(await reverseGeocodeLabel(coords));
        setLocationDenied(false);
      } else {
        setLocationDenied(denied);
      }
    } finally {
      setLocating(false);
    }
  };

  const hasLocation = f.userLocation !== null;

  const sorted = useMemo(() => {
    if (!f.userLocation) return [];
    const filtered = stations
      .filter((s) => s.fuels[f.selectedFuel])
      .filter((s) => f.selectedBrands.size === 0 || (s.brand && f.selectedBrands.has(s.brand)))
      .filter((s) => !f.openH24Only || s.h24 === true);
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
  }, [stations, f.selectedFuel, f.selectedBrands, f.openH24Only, f.sortBy, f.userLocation]);

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
            <div className="flex flex-col sm:flex-row gap-2">
              <div className="flex-1 min-w-0">
                <SearchBar
                  initialLabel={f.searchLabel}
                  onResult={(r) => {
                    f.setUserLocation({ lat: r.lat, lng: r.lng });
                    f.setSearchLabel(`${r.postcode} ${r.city}`);
                  }}
                />
              </div>
              <button
                onClick={onLocateMe}
                disabled={locating}
                className={[
                  'shrink-0 px-4 py-2 rounded-xl text-body-sm font-semibold inline-flex items-center justify-center gap-2 active:scale-95 transition-transform shadow-sm border disabled:opacity-70',
                  locationDenied
                    ? 'bg-error-container text-on-error-container border-error'
                    : 'bg-primary text-on-primary border-transparent',
                ].join(' ')}
                aria-label="Me localiser"
              >
                <Icon name={locating ? 'sync' : locationDenied ? 'location_disabled' : 'my_location'} filled size={16} />
                {locating ? 'Localisation…' : locationDenied ? 'Réessayer' : 'Me localiser'}
              </button>
            </div>
            {locationDenied && (
              <p className="text-body-sm text-error flex items-start gap-2">
                <Icon name="info" size={16} />
                <span>
                  Localisation refusée. Saisis une ville ou un code postal ci-dessus.
                </span>
              </p>
            )}
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
            {hasLocation && (
              <p className="text-body-sm text-on-surface-variant">
                Rayon : {f.radiusKm} km {f.searchLabel ? `autour de ${f.searchLabel}` : ''}
              </p>
            )}
          </section>

          <section className="flex flex-col gap-gutter">
            {!hasLocation && (
              <div className="text-center py-xl space-y-2">
                <Icon name="location_searching" size={32} className="text-primary mx-auto" />
                <p className="text-body-lg text-on-surface">
                  Indique ta position pour voir les stations.
                </p>
                <p className="text-body-sm text-on-surface-variant">
                  Saisis une ville ou utilise le bouton « Me localiser ».
                </p>
              </div>
            )}
            {hasLocation && loading && sorted.length === 0 && (
              <p className="text-center text-body-sm text-on-surface-variant py-lg">
                Chargement des stations…
              </p>
            )}
            {hasLocation && !loading && sorted.length === 0 && (
              <p className="text-center text-body-sm text-on-surface-variant py-lg">
                {f.openH24Only
                  ? `Aucune station ${f.selectedFuel} ouverte 24/7 dans ce rayon. Ajustez les filtres.`
                  : `Aucune station ${f.selectedFuel} dans ce rayon. Ajustez les filtres.`}
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
