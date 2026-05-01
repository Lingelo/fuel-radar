import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import type { Coords, FuelType } from '../types';
import { FUEL_TYPES } from '../types';

export type SortBy = 'price' | 'distance';

const KEY = 'fuelfinder-filters-v1';
const LOCATION_KEY = 'fuelfinder-last-location-v1';

interface PersistedFilters {
  selectedFuel: FuelType;
  radiusKm: number;
  selectedBrands: string[];
  sortBy: SortBy;
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
    return { selectedFuel: fuel, radiusKm: radius, selectedBrands: brands, sortBy: sort };
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
  /** Reset filters to factory defaults (does not touch user location). */
  resetFilters: () => void;
  userLocation: Coords | null;
  setUserLocation: (c: Coords | null) => void;
  searchLabel: string | null;
  setSearchLabel: (s: string | null) => void;
}

const FiltersContext = createContext<FiltersState | null>(null);

export function FiltersProvider({ children }: { children: ReactNode }) {
  const initial = loadPersisted();
  const lastLoc = loadLastLocation();
  const [selectedFuel, setSelectedFuel] = useState<FuelType>(initial.selectedFuel);
  const [radiusKm, setRadiusKm] = useState(initial.radiusKm);
  const [selectedBrands, setSelectedBrands] = useState<Set<string>>(new Set(initial.selectedBrands));
  const [sortBy, setSortBy] = useState<SortBy>(initial.sortBy);
  // Initialise from last-known location for instant UI; Bootstrap will
  // refresh it in the background.
  const [userLocation, setUserLocation] = useState<Coords | null>(
    lastLoc ? { lat: lastLoc.lat, lng: lastLoc.lng } : null,
  );
  const [searchLabel, setSearchLabel] = useState<string | null>(lastLoc?.label ?? null);

  // Persist filters whenever they change.
  useEffect(() => {
    try {
      const payload: PersistedFilters = {
        selectedFuel,
        radiusKm,
        selectedBrands: [...selectedBrands],
        sortBy,
      };
      localStorage.setItem(KEY, JSON.stringify(payload));
    } catch {
      // ignore quota / sandboxed storage errors
    }
  }, [selectedFuel, radiusKm, selectedBrands, sortBy]);

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
      resetFilters,
      userLocation,
      setUserLocation,
      searchLabel,
      setSearchLabel,
    }),
    [selectedFuel, radiusKm, selectedBrands, sortBy, userLocation, searchLabel],
  );

  return <FiltersContext.Provider value={value}>{children}</FiltersContext.Provider>;
}

export function useFilters(): FiltersState {
  const ctx = useContext(FiltersContext);
  if (!ctx) throw new Error('useFilters must be used inside <FiltersProvider>');
  return ctx;
}
