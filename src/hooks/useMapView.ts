import { useState, useCallback } from 'react';
import type { LatLngBoundsExpression } from 'leaflet';
import L from 'leaflet';

interface MapViewState {
  center: [number, number];
  zoom: number;
  bounds: LatLngBoundsExpression | null;
}

const FRANCE_CENTER: [number, number] = [46.603354, 1.888334];
const FRANCE_ZOOM = 6;

export function useMapView() {
  const [view, setView] = useState<MapViewState>({
    center: FRANCE_CENTER,
    zoom: FRANCE_ZOOM,
    bounds: null,
  });

  const flyToCity = useCallback((lat: number, lng: number) => {
    // Zoom to city level (~5 km diameter), not the full search radius
    const center = L.latLng(lat, lng);
    const bounds = center.toBounds(5 * 1000); // 5 km diameter = city level
    setView({ center: [lat, lng], zoom: FRANCE_ZOOM, bounds });
  }, []);

  const flyToStation = useCallback((lat: number, lng: number) => {
    // Tighter than flyToCity — center precisely on the marker, zoom in
    const center = L.latLng(lat, lng);
    const bounds = center.toBounds(800); // 800m diameter = street level
    setView({ center: [lat, lng], zoom: FRANCE_ZOOM, bounds });
  }, []);

  const resetView = useCallback(() => {
    setView({ center: FRANCE_CENTER, zoom: FRANCE_ZOOM, bounds: null });
  }, []);

  return { ...view, flyToCity, flyToStation, resetView };
}
