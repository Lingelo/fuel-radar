import { useEffect } from 'react';
import { FiltersProvider, useFilters } from './state/FiltersContext';
import { ViewProvider, useViewNav } from './state/ViewContext';
import { FavoritesProvider } from './state/FavoritesContext';
import { SettingsProvider, useSettings } from './state/SettingsContext';
import { TopAppBar } from './components/TopAppBar';
import { BottomNavBar } from './components/BottomNavBar';
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
    if (userLocation) return;
    let cancelled = false;
    (async () => {
      const coords = await getBrowserLocation();
      if (cancelled) return;
      if (coords) {
        setUserLocation(coords);
        const addr = await reverseGeocode(coords);
        if (cancelled) return;
        if (addr) setSearchLabel(`${addr.postcode} ${addr.city}`);
        else setSearchLabel(`${coords.lat.toFixed(3)}, ${coords.lng.toFixed(3)}`);
      } else {
        setUserLocation({ lat: 48.8566, lng: 2.3522 });
        setSearchLabel('Paris');
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [userLocation, setUserLocation, setSearchLabel]);

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
            </div>
          </ViewProvider>
        </FiltersProvider>
      </FavoritesProvider>
    </SettingsProvider>
  );
}
