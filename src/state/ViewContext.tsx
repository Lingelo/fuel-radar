import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import type { View } from '../types';

interface ViewState {
  view: View;
  /** Optional focus station id, consumed once when MapScreen reads it. */
  focusStationId: number | null;
  consumeFocusStation: () => number | null;
  goMap: (focusStationId?: number) => void;
  goStations: () => void;
  goFavorites: () => void;
  goTrends: () => void;
  goSettings: () => void;
  goDetails: (stationId: number) => void;
  goBack: () => void;
}

const ViewContext = createContext<ViewState | null>(null);

/** Map a View to a hash fragment used in window.location.hash. */
function viewToHash(view: View): string {
  switch (view.kind) {
    case 'map':
      return '';
    case 'stations':
      return '#/stations';
    case 'favorites':
      return '#/favoris';
    case 'trends':
      return '#/tendances';
    case 'settings':
      return '#/reglages';
    case 'details':
      return `#/station/${view.stationId}`;
  }
}

/** Inverse of viewToHash — returns null if the hash doesn't match a known route. */
function hashToView(hash: string): View | null {
  const h = hash.replace(/^#\/?/, '');
  if (!h || h === '' || h === '/') return { kind: 'map' };
  if (h === 'stations') return { kind: 'stations' };
  if (h === 'favoris') return { kind: 'favorites' };
  if (h === 'tendances') return { kind: 'trends' };
  if (h === 'reglages') return { kind: 'settings' };
  const m = h.match(/^station\/(\d+)$/);
  if (m) return { kind: 'details', stationId: Number(m[1]) };
  return null;
}

export function ViewProvider({ children }: { children: ReactNode }) {
  const [stack, setStack] = useState<View[]>(() => {
    if (typeof window === 'undefined') return [{ kind: 'map' }];
    const fromHash = hashToView(window.location.hash);
    return [fromHash ?? { kind: 'map' }];
  });
  const [focusStationId, setFocusStationId] = useState<number | null>(null);

  const view = stack[stack.length - 1];

  // Reflect the current view in the URL hash so it can be bookmarked / shared.
  useEffect(() => {
    const next = viewToHash(view);
    const current = window.location.hash;
    if (current === next) return;
    if (current === '' && next === '') return;
    window.history.pushState(
      null,
      '',
      `${window.location.pathname}${window.location.search}${next}`,
    );
  }, [view]);

  // Browser back/forward + manual hash edit → update internal stack.
  useEffect(() => {
    const onPop = () => {
      const fromHash = hashToView(window.location.hash);
      if (fromHash) setStack([fromHash]);
    };
    window.addEventListener('popstate', onPop);
    window.addEventListener('hashchange', onPop);
    return () => {
      window.removeEventListener('popstate', onPop);
      window.removeEventListener('hashchange', onPop);
    };
  }, []);

  const push = useCallback((v: View) => setStack((s) => [...s, v]), []);
  const replaceRoot = useCallback((v: View) => setStack(() => [v]), []);

  const goMap = useCallback(
    (id?: number) => {
      if (typeof id === 'number') setFocusStationId(id);
      replaceRoot({ kind: 'map' });
    },
    [replaceRoot],
  );

  const consumeFocusStation = useCallback(() => {
    const id = focusStationId;
    setFocusStationId(null);
    return id;
  }, [focusStationId]);

  const goStations = useCallback(() => replaceRoot({ kind: 'stations' }), [replaceRoot]);
  const goFavorites = useCallback(() => replaceRoot({ kind: 'favorites' }), [replaceRoot]);
  const goTrends = useCallback(() => replaceRoot({ kind: 'trends' }), [replaceRoot]);
  const goSettings = useCallback(() => replaceRoot({ kind: 'settings' }), [replaceRoot]);
  const goDetails = useCallback((stationId: number) => push({ kind: 'details', stationId }), [push]);
  const goBack = useCallback(() => {
    setStack((s) => (s.length > 1 ? s.slice(0, -1) : s));
  }, []);

  const value = useMemo(
    () => ({
      view,
      focusStationId,
      consumeFocusStation,
      goMap,
      goStations,
      goFavorites,
      goTrends,
      goSettings,
      goDetails,
      goBack,
    }),
    [view, focusStationId, consumeFocusStation, goMap, goStations, goFavorites, goTrends, goSettings, goDetails, goBack],
  );

  return <ViewContext.Provider value={value}>{children}</ViewContext.Provider>;
}

export function useViewNav(): ViewState {
  const ctx = useContext(ViewContext);
  if (!ctx) throw new Error('useViewNav must be used inside <ViewProvider>');
  return ctx;
}
