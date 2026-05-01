export type FuelType = 'Gazole' | 'SP95' | 'SP98' | 'E10' | 'E85' | 'GPLc';

export const FUEL_TYPES: FuelType[] = ['Gazole', 'SP95', 'E10', 'SP98', 'E85', 'GPLc'];

export interface FuelPrice {
  /** Price in euros */
  p: number;
  /** Last update date (YYYY-MM-DD) */
  d: string;
}

export interface Station {
  id: number;
  lat: number;
  lng: number;
  addr: string;
  city: string;
  cp: string;
  brand?: string;
  /** Open 24/7 via automatic dispenser (from XML automate-24-24 attribute). */
  h24?: boolean;
  /** Free-form service labels straight from the gov XML (e.g. "Lavage automatique"). */
  services?: string[];
  fuels: Partial<Record<FuelType, FuelPrice>>;
}

export interface MetaData {
  lastUpdate: string;
}

/** Per-station price history: stationId -> fuel -> [epoch, price][] */
export type StationHistoryData = Record<string, Record<string, [number, number][]>>;

export type View =
  | { kind: 'map' }
  | { kind: 'stations' }
  | { kind: 'favorites' }
  | { kind: 'trends' }
  | { kind: 'settings' }
  | { kind: 'details'; stationId: number };

export interface Coords {
  lat: number;
  lng: number;
}
