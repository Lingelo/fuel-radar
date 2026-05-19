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
import { getBrowserLocation, reverseGeocodeLabel } from './lib/geocode';
import { clearZoneShareFromUrl } from './lib/shareUrl';

function Bootstrap() {
  const { setUserLocation, setSearchLabel, hydratedFromShare, userLocation } = useFilters();
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
      // If we hydrated from a shared zone URL, resolve a human-readable
      // label for the shared coords (so the search bar shows "75001 Paris"
      // instead of staying blank) and skip the GPS lookup — the share
      // explicitly points to a zone the user wants to inspect.
      if (hydratedFromShare) {
        clearZoneShareFromUrl();
        if (userLocation) {
          const label = await reverseGeocodeLabel(userLocation);
          if (!cancelled) setSearchLabel(label);
        }
        return;
      }
      // Always re-query the browser geolocation in the background so the
      // user lands on a fresh position. The FiltersProvider already seeds
      // userLocation with the last-known persisted value at boot, so the
      // UI never starts blank — it just gets refined when GPS resolves.
      const { coords } = await getBrowserLocation();
      if (cancelled) return;
      if (!coords) {
        // Geolocation refused or unavailable. We don't fall back to a
        // hard-coded city anymore — let the UI surface a welcome screen
        // that asks the user to type an address instead.
        return;
      }
      const label = await reverseGeocodeLabel(coords);
      if (cancelled) return;
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
              <main className="flex-grow relative mt-16 mb-[calc(4rem+env(safe-area-inset-bottom))] md:mb-0 overflow-hidden">
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
