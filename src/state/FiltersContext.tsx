import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import type { Coords, FuelType } from '../types';
import { FUEL_TYPES } from '../types';
import { parseZoneShare, type ZoneShareState } from '../lib/shareUrl';

export type SortBy = 'price' | 'distance';

const KEY = 'fuelfinder-filters-v1';
const LOCATION_KEY = 'fuelfinder-last-location-v1';

interface PersistedFilters {
  selectedFuel: FuelType;
  radiusKm: number;
  selectedBrands: string[];
  sortBy: SortBy;
  openH24Only: boolean;
}

interface PersistedLocation {
  lat: number;
  lng: number;
  label: string | null;
}

const DEFAULTS: PersistedFilters = {
  selectedFuel: 'Gazole',
  radiusKm: 10,
  selectedBrands: [],
  sortBy: 'price',
  openH24Only: false,
};

function loadPersisted(): PersistedFilters {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return DEFAULTS;
    const parsed = JSON.parse(raw) as Partial<PersistedFilters>;
    const fuel = (FUEL_TYPES as string[]).includes(parsed.selectedFuel as string)
      ? (parsed.selectedFuel as FuelType)
      : DEFAULTS.selectedFuel;
    const radius = Math.min(30, Math.max(1, Number(parsed.radiusKm) || DEFAULTS.radiusKm));
    const sort: SortBy = parsed.sortBy === 'distance' ? 'distance' : 'price';
    const brands = Array.isArray(parsed.selectedBrands) ? parsed.selectedBrands : [];
    const openH24Only = parsed.openH24Only === true;
    return {
      selectedFuel: fuel,
      radiusKm: radius,
      selectedBrands: brands,
      sortBy: sort,
      openH24Only,
    };
  } catch {
    return DEFAULTS;
  }
}

/**
 * Last-known location persisted across reloads. Used as the initial value
 * so the user lands on a familiar map immediately while a fresh GPS
 * lookup happens in the background.
 */
function loadLastLocation(): PersistedLocation | null {
  try {
    const raw = localStorage.getItem(LOCATION_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<PersistedLocation>;
    if (typeof parsed.lat !== 'number' || typeof parsed.lng !== 'number') return null;
    return {
      lat: parsed.lat,
      lng: parsed.lng,
      label: typeof parsed.label === 'string' ? parsed.label : null,
    };
  } catch {
    return null;
  }
}

export interface FiltersState {
  selectedFuel: FuelType;
  setSelectedFuel: (f: FuelType) => void;
  radiusKm: number;
  setRadiusKm: (km: number) => void;
  selectedBrands: Set<string>;
  setSelectedBrands: (brands: Set<string>) => void;
  sortBy: SortBy;
  setSortBy: (s: SortBy) => void;
  openH24Only: boolean;
  setOpenH24Only: (v: boolean) => void;
  /** Reset filters to factory defaults (does not touch user location). */
  resetFilters: () => void;
  userLocation: Coords | null;
  setUserLocation: (c: Coords | null) => void;
  searchLabel: string | null;
  setSearchLabel: (s: string | null) => void;
  /**
   * `true` when the initial state was hydrated from a `?lat&lng…` share URL.
   * Consumed by the Bootstrap effect to skip the background GPS lookup that
   * would otherwise overwrite the shared zone.
   */
  hydratedFromShare: boolean;
}

const FiltersContext = createContext<FiltersState | null>(null);

export function FiltersProvider({ children }: { children: ReactNode }) {
  const initial = loadPersisted();
  const lastLoc = loadLastLocation();
  // A shared zone URL takes precedence over both persisted state and GPS.
  const shared: ZoneShareState | null =
    typeof window !== 'undefined' ? parseZoneShare(window.location.search) : null;
  const hydratedFromShare = shared !== null;

  const [selectedFuel, setSelectedFuel] = useState<FuelType>(
    shared?.fuel ?? initial.selectedFuel,
  );
  const [radiusKm, setRadiusKm] = useState(shared?.radiusKm ?? initial.radiusKm);
  const [selectedBrands, setSelectedBrands] = useState<Set<string>>(
    new Set(shared?.brands ?? initial.selectedBrands),
  );
  const [sortBy, setSortBy] = useState<SortBy>(initial.sortBy);
  const [openH24Only, setOpenH24Only] = useState<boolean>(
    shared?.openH24Only ?? initial.openH24Only,
  );
  // Initialise from share URL if present, otherwise last-known location for
  // instant UI; Bootstrap will refresh it in the background unless a shared
  // zone is active.
  const [userLocation, setUserLocation] = useState<Coords | null>(
    shared?.coords ?? (lastLoc ? { lat: lastLoc.lat, lng: lastLoc.lng } : null),
  );
  const [searchLabel, setSearchLabel] = useState<string | null>(
    shared ? null : lastLoc?.label ?? null,
  );

  // Persist filters whenever they change.
  useEffect(() => {
    try {
      const payload: PersistedFilters = {
        selectedFuel,
        radiusKm,
        selectedBrands: [...selectedBrands],
        sortBy,
        openH24Only,
      };
      localStorage.setItem(KEY, JSON.stringify(payload));
    } catch {
      // ignore quota / sandboxed storage errors
    }
  }, [selectedFuel, radiusKm, selectedBrands, sortBy, openH24Only]);

  // Persist last-known location whenever it resolves to real coords.
  useEffect(() => {
    if (!userLocation) return;
    try {
      const payload: PersistedLocation = {
        lat: userLocation.lat,
        lng: userLocation.lng,
        label: searchLabel,
      };
      localStorage.setItem(LOCATION_KEY, JSON.stringify(payload));
    } catch {
      // ignore
    }
  }, [userLocation, searchLabel]);

  const resetFilters = () => {
    setSelectedFuel(DEFAULTS.selectedFuel);
    setRadiusKm(DEFAULTS.radiusKm);
    setSelectedBrands(new Set());
    setSortBy(DEFAULTS.sortBy);
    setOpenH24Only(DEFAULTS.openH24Only);
  };

  const value = useMemo<FiltersState>(
    () => ({
      selectedFuel,
      setSelectedFuel,
      radiusKm,
      setRadiusKm,
      selectedBrands,
      setSelectedBrands,
      sortBy,
      setSortBy,
      openH24Only,
      setOpenH24Only,
      resetFilters,
      userLocation,
      setUserLocation,
      searchLabel,
      setSearchLabel,
      hydratedFromShare,
    }),
    [selectedFuel, radiusKm, selectedBrands, sortBy, openH24Only, userLocation, searchLabel, hydratedFromShare],
  );

  return <FiltersContext.Provider value={value}>{children}</FiltersContext.Provider>;
}

export function useFilters(): FiltersState {
  const ctx = useContext(FiltersContext);
  if (!ctx) throw new Error('useFilters must be used inside <FiltersProvider>');
  return ctx;
}
