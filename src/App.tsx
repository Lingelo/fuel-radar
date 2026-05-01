import { useEffect } from 'react';
import { FiltersProvider, useFilters } from './state/FiltersContext';
import { ViewProvider, useViewNav } from './state/ViewContext';
import { FavoritesProvider } from './state/FavoritesContext';
import { SettingsProvider, useSettings } from './state/SettingsContext';
import { TopAppBar } from './components/TopAppBar';
import { BottomNavBar } from './components/BottomNavBar';
import { UpdateBanner } from './components/UpdateBanner';
import { MapScreen } from './screens/MapScreen';
import { StationsScreen } from './screens/StationsScreen';
import { StationDetailScreen } from './screens/StationDetailScreen';
import { FavoritesScreen } from './screens/FavoritesScreen';
import { TrendsScreen } from './screens/TrendsScreen';
import { SettingsScreen } from './screens/SettingsScreen';
import { getBrowserLocation, reverseGeocode } from './lib/geocode';

function Bootstrap() {
  const { userLocation, setUserLocation, setSearchLabel } = useFilters();
  const settings = useSettings();
  const nav = useViewNav();

  useEffect(() => {
    if (settings.defaultStart === 'stations' && nav.view.kind === 'map') {
      nav.goStations();
    }
    // Run once on mount
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      // Always re-query the browser geolocation in the background so the
      // user lands on a fresh position. The FiltersProvider already seeds
      // userLocation with the last-known persisted value at boot, so the
      // UI never starts blank — it just gets refined when GPS resolves.
      const coords = await getBrowserLocation();
      if (cancelled) return;
      if (!coords) {
        // No coords + no previous value → fall back to Paris.
        if (!userLocation) {
          setUserLocation({ lat: 48.8566, lng: 2.3522 });
          setSearchLabel('Paris');
        }
        return;
      }
      const addr = await reverseGeocode(coords);
      if (cancelled) return;
      const label = addr
        ? `${addr.postcode} ${addr.city}`
        : `${coords.lat.toFixed(3)}, ${coords.lng.toFixed(3)}`;
      setUserLocation(coords);
      setSearchLabel(label);
    })();
    return () => {
      cancelled = true;
    };
    // Run exactly once on mount.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return null;
}

function Router() {
  const { view } = useViewNav();
  switch (view.kind) {
    case 'map':
      return <MapScreen />;
    case 'stations':
      return <StationsScreen />;
    case 'favorites':
      return <FavoritesScreen />;
    case 'trends':
      return <TrendsScreen />;
    case 'settings':
      return <SettingsScreen />;
    case 'details':
      return <StationDetailScreen stationId={view.stationId} />;
  }
}

export function App() {
  return (
    <SettingsProvider>
      <FavoritesProvider>
        <FiltersProvider>
          <ViewProvider>
            <Bootstrap />
            <div className="h-screen w-screen overflow-hidden flex flex-col bg-background text-on-background">
              <TopAppBar />
              <main className="flex-grow relative mt-16 mb-16 md:mb-0 overflow-hidden">
                <Router />
              </main>
              <BottomNavBar />
              <UpdateBanner />
            </div>
          </ViewProvider>
        </FiltersProvider>
      </FavoritesProvider>
    </SettingsProvider>
  );
}
