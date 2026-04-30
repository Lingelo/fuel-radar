import { useState, useCallback, useMemo, useEffect, useRef } from 'react';
import type { LatLngBounds } from 'leaflet';
import type { CityResult, FuelType, Station } from './types';
import { useCitySearch } from './hooks/useCitySearch';
import { useStations } from './hooks/useStations';
import { useMapView } from './hooks/useMapView';
import { filterByRadius, getDepartmentFromPostalCode, reverseGeocode } from './utils/geo';
import { getDepartmentWithNeighbors } from './utils/departments';
import { MapView } from './components/MapView';
import { SearchBar } from './components/SearchBar';
import { Header } from './components/Header';
import { FuelFilter } from './components/FuelFilter';
import { StationPanel } from './components/StationPanel';
import { AboutModal } from './components/AboutModal';
import { PriceHistoryModal } from './components/PriceHistoryModal';
import { Footer } from './components/Footer';
import { useStationHistory } from './hooks/useStationHistory';
import { timeAgo, FUEL_LABELS, getFuelPrice, getPriceBounds } from './utils/fuel';

const SEARCH_RADIUS_KM = 10;

interface StationWithDistance extends Station {
  distance: number;
}

export default function App() {
  const { query, search, results, loading: searchLoading, error: searchError, searched: searchSearched, retry: searchRetry, setResults, setQuery } = useCitySearch();
  const { stations, loading: stationsLoading, error, meta, loadDepartments, resetStations } = useStations();
  const { center, zoom, bounds, flyToCity, flyToStation } = useMapView();

  const [selectedFuel, setSelectedFuel] = useState<FuelType>('Gazole');
  const [selectedCity, setSelectedCity] = useState<CityResult | null>(null);
  const [selectedStationId, setSelectedStationId] = useState<number | null>(null);
  const [hoveredStationId, setHoveredStationId] = useState<number | null>(null);
  const [panelOpen, setPanelOpen] = useState(false);
  const [mapBounds, setMapBounds] = useState<LatLngBounds | null>(null);
  const [showAbout, setShowAbout] = useState(false);
  const [showHistory, setShowHistory] = useState(false);
  const [geolocating, setGeolocating] = useState(false);
  const [geoError, setGeoError] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  const prevFuelRef = useRef<FuelType>(selectedFuel);
  const { getStationHistory } = useStationHistory();

  const nearbyStations: StationWithDistance[] = useMemo(() => {
    if (!selectedCity) return [];
    return filterByRadius(stations, selectedCity.lat, selectedCity.lng, SEARCH_RADIUS_KM);
  }, [stations, selectedCity]);

  // Stations visible in the current map viewport
  const visibleStations: StationWithDistance[] = useMemo(() => {
    if (!mapBounds) return nearbyStations;
    return nearbyStations.filter((s) => mapBounds.contains([s.lat, s.lng]));
  }, [nearbyStations, mapBounds]);

  // Compute price bounds once from all nearby stations for consistent coloring
  const priceBounds = useMemo(() => {
    const prices = nearbyStations
      .map(s => getFuelPrice(s, selectedFuel))
      .filter((p): p is number => p !== null);
    if (prices.length === 0) return { pMin: 0, pMax: 0 };
    return getPriceBounds(prices);
  }, [nearbyStations, selectedFuel]);

  const handleCitySelect = useCallback(
    async (city: CityResult) => {
      setSelectedCity(city);
      setSelectedStationId(null);
      setQuery(city.name);
      setPanelOpen(true);

      resetStations();
      const dept = getDepartmentFromPostalCode(city.postcode);
      const depts = getDepartmentWithNeighbors(dept);
      await loadDepartments(depts);
      flyToCity(city.lat, city.lng);
    },
    [search, setResults, loadDepartments, resetStations, flyToCity],
  );

  const handleStationClick = useCallback((station: StationWithDistance) => {
    setSelectedStationId(station.id);
    flyToStation(station.lat, station.lng);
  }, [flyToStation]);

  const handleClear = useCallback(() => {
    setResults([]);
  }, [setResults]);

  const handleBoundsChange = useCallback((b: LatLngBounds) => {
    setMapBounds(b);
  }, []);

  // Geolocation handler
  const handleGeolocate = useCallback(async () => {
    if (!navigator.geolocation) {
      setGeoError('La géolocalisation n\'est pas supportée par votre navigateur');
      return;
    }
    setGeolocating(true);
    setGeoError(null);

    try {
      const position = await new Promise<GeolocationPosition>((resolve, reject) => {
        navigator.geolocation.getCurrentPosition(resolve, reject, {
          enableHighAccuracy: true,
          timeout: 10000,
        });
      });

      const { latitude, longitude } = position.coords;

      // Check if within France bounds (roughly)
      if (latitude < 41.2 || latitude > 51.5 || longitude < -5.5 || longitude > 10) {
        setGeoError('Cette application couvre uniquement la France');
        setGeolocating(false);
        return;
      }

      const result = await reverseGeocode(latitude, longitude);
      if (!result) {
        setGeoError('Impossible de déterminer votre ville');
        setGeolocating(false);
        return;
      }

      const city: CityResult = {
        name: result.name,
        postcode: result.postcode,
        departmentCode: result.departmentCode,
        lat: latitude,
        lng: longitude,
      };
      await handleCitySelect(city);
    } catch (err) {
      const geoErr = err as GeolocationPositionError;
      if (geoErr.code === 1) {
        setGeoError('Activez la géolocalisation dans votre navigateur');
      } else {
        setGeoError('Impossible d\'obtenir votre position');
      }
    } finally {
      setGeolocating(false);
    }
  }, [handleCitySelect]);

  // Clear geo error after 4s
  useEffect(() => {
    if (!geoError) return;
    const t = setTimeout(() => setGeoError(null), 4000);
    return () => clearTimeout(t);
  }, [geoError]);

  // Toast on fuel change (only after a city is selected)
  useEffect(() => {
    if (prevFuelRef.current === selectedFuel) return;
    prevFuelRef.current = selectedFuel;
    if (!selectedCity || nearbyStations.length === 0) return;

    const count = nearbyStations.filter(
      (s) => s.fuels[selectedFuel] !== undefined,
    ).length;
    setToast(`${count} station${count > 1 ? 's' : ''} propose${count > 1 ? 'nt' : ''} ${FUEL_LABELS[selectedFuel]} dans cette zone`);
  }, [selectedFuel, selectedCity, nearbyStations]);

  // Auto-hide toast after 3s
  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(null), 3000);
    return () => clearTimeout(t);
  }, [toast]);

  return (
    <div className="relative h-full w-full overflow-hidden">
      {/* Skip-nav link — visually hidden until focused (WCAG 2.4.1) */}
      <a
        href="#city-search"
        className="sr-only focus:not-sr-only focus:absolute focus:left-3 focus:top-3 focus:z-[2000] focus:rounded-md focus:bg-primary focus:px-3 focus:py-1.5 focus:text-sm focus:font-medium focus:text-white focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2"
      >
        Aller à la recherche
      </a>

      {/* Map — still receives ALL nearbyStations so markers exist even outside viewport */}
      <MapView
        center={center}
        zoom={zoom}
        bounds={bounds}
        stations={nearbyStations}
        selectedFuel={selectedFuel}
        selectedStationId={selectedStationId}
        onStationSelect={setSelectedStationId}
        getStationHistory={getStationHistory}
        onVisibleBoundsChange={handleBoundsChange}
        searchCenter={selectedCity ? [selectedCity.lat, selectedCity.lng] : null}
        searchRadius={SEARCH_RADIUS_KM}
        priceBounds={priceBounds}
        hoveredStationId={hoveredStationId}
        onGeolocate={handleGeolocate}
        geolocating={geolocating}
        hasPanel={!!selectedCity}
        panelOpen={panelOpen}
      />

      {/* Top overlay: search + filters */}
      <div className="absolute left-0 right-0 top-0 z-10 p-3 md:p-4">
        <div className="mx-auto flex max-w-2xl flex-col gap-2">
          {/* Header row */}
          <div className="glass flex items-center justify-between gap-3 rounded-2xl px-4 py-2 shadow-lg">
            <Header />
            <div className="hidden md:block">
              <FuelFilter selected={selectedFuel} onChange={setSelectedFuel} />
            </div>
          </div>

          {/* Search */}
          <SearchBar
            query={query}
            onSearch={search}
            results={results}
            loading={searchLoading}
            error={searchError}
            searched={searchSearched}
            onRetry={searchRetry}
            onSelect={handleCitySelect}
            onClear={handleClear}
          />

          {/* Mobile fuel filter */}
          <div className="glass rounded-xl px-3 py-2 shadow-md md:hidden">
            <FuelFilter selected={selectedFuel} onChange={setSelectedFuel} />
          </div>
        </div>
      </div>

      {/* Loading indicator */}
      {stationsLoading && (
        <div className="absolute left-1/2 top-1/2 z-20 -translate-x-1/2 -translate-y-1/2">
          <div className="glass rounded-xl px-6 py-3 shadow-lg">
            <div className="flex items-center gap-2 text-sm text-gray-600">
              <svg className="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
              </svg>
              Chargement...
            </div>
          </div>
        </div>
      )}

      {/* Error */}
      {error && (
        <div className="absolute left-1/2 top-1/2 z-20 -translate-x-1/2 -translate-y-1/2">
          <div className="rounded-xl bg-red-50 px-6 py-3 text-sm text-red-600 shadow-lg">
            {error}
          </div>
        </div>
      )}

      {/* Geo error */}
      {geoError && (
        <div className="absolute bottom-24 left-1/2 z-20 -translate-x-1/2 md:bottom-8" role="alert">
          <div className="rounded-xl bg-red-50 px-5 py-2.5 text-sm text-red-600 shadow-lg whitespace-nowrap">
            {geoError}
          </div>
        </div>
      )}

      {/* Welcome overlay — visible only when no city selected and not loading */}
      {!selectedCity && !stationsLoading && (
        <div className="absolute inset-0 z-10 flex items-center justify-center pointer-events-none">
          <div className="glass pointer-events-auto mx-4 flex max-w-sm flex-col items-center rounded-3xl px-8 py-10 text-center shadow-2xl">
            <h2 className="mb-1 text-xl font-bold text-gray-800">Trouvez le carburant le moins cher</h2>
            <p className="mb-6 text-sm text-gray-500">Recherchez votre ville ou utilisez votre position</p>
            <button
              onClick={handleGeolocate}
              disabled={geolocating}
              className="flex items-center gap-2 rounded-xl bg-primary px-6 py-3 text-sm font-semibold text-white shadow-md transition-colors hover:opacity-90 disabled:opacity-60"
            >
              {geolocating ? (
                <svg className="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                </svg>
              ) : (
                <svg className="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="12" cy="12" r="3" />
                  <path d="M12 2v4M12 18v4M2 12h4M18 12h4" />
                </svg>
              )}
              Me localiser
            </button>
            <button
              onClick={() => document.getElementById('city-search')?.focus()}
              className="mt-6 flex items-center gap-1 text-xs text-gray-400 transition-colors hover:text-gray-600"
            >
              <svg className="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M5 12h14M12 5l7 7-7 7" />
              </svg>
              <span>ou tapez une ville ci-dessus</span>
            </button>
          </div>
        </div>
      )}

      <Footer
        onShowAbout={() => setShowAbout(true)}
        onShowHistory={() => setShowHistory(true)}
        lastUpdate={meta?.lastUpdate}
        hasCity={!!selectedCity}
      />

      {showAbout && <AboutModal onClose={() => setShowAbout(false)} lastUpdate={meta?.lastUpdate} />}
      {showHistory && <PriceHistoryModal onClose={() => setShowHistory(false)} />}

      {/* Station panel — uses visibleStations (synced with map viewport) */}
      {selectedCity && (
        <>
          {/* Desktop panel */}
          <div className="absolute bottom-0 right-0 top-0 z-10 hidden w-72 border-l border-gray-200 bg-white shadow-xl md:block">
            <StationPanel
              stations={visibleStations}
              totalStations={nearbyStations.length}
              selectedFuel={selectedFuel}
              onStationClick={handleStationClick}
              selectedStationId={selectedStationId}
              priceBounds={priceBounds}
              onStationHover={setHoveredStationId}
            />
          </div>

          {/* Mobile bottom sheet */}
          <div className="absolute bottom-0 left-0 right-0 z-10 md:hidden">
            <button
              onClick={() => setPanelOpen(!panelOpen)}
              className="glass flex w-full items-center justify-between rounded-t-2xl px-4 py-2.5 shadow-[0_-4px_20px_rgba(0,0,0,0.1)]"
            >
              <div>
                <span className="text-sm font-semibold text-gray-700">
                  {visibleStations.length} station{visibleStations.length > 1 ? 's' : ''} visibles
                </span>
                <div className="flex items-center gap-1.5 text-[10px] text-gray-400">
                  <span
                    onClick={(e) => { e.stopPropagation(); setShowAbout(true); }}
                    className="underline decoration-gray-300 underline-offset-2"
                  >
                    À propos
                  </span>
                  <span>·</span>
                  <span
                    onClick={(e) => { e.stopPropagation(); setShowHistory(true); }}
                    className="underline decoration-gray-300 underline-offset-2"
                  >
                    Prix
                  </span>
                  {meta?.lastUpdate && (
                    <>
                      <span>·</span>
                      <span>MAJ {timeAgo(meta.lastUpdate)}</span>
                    </>
                  )}
                </div>
              </div>
              <svg
                className={`h-4 w-4 text-gray-400 transition-transform ${panelOpen ? 'rotate-180' : ''}`}
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 15l7-7 7 7" />
              </svg>
            </button>
            {panelOpen && (
              <div className="h-64 bg-white">
                <StationPanel
                  stations={visibleStations}
                  totalStations={nearbyStations.length}
                  selectedFuel={selectedFuel}
                  onStationClick={handleStationClick}
                  selectedStationId={selectedStationId}
                  priceBounds={priceBounds}
                  onStationHover={setHoveredStationId}
                />
              </div>
            )}
          </div>
        </>
      )}

      {/* Toast — fuel change feedback */}
      {toast && (
        <div className="absolute bottom-24 left-1/2 z-20 -translate-x-1/2 md:bottom-8">
          <div className="glass rounded-xl px-5 py-2.5 text-sm font-medium text-gray-700 shadow-lg">
            {toast}
          </div>
        </div>
      )}
    </div>
  );
}
