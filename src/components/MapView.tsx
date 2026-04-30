import { useEffect, useRef, useMemo, useState } from 'react';
import { MapContainer, TileLayer, useMap } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet.markercluster';
import type { FuelType, Station } from '../types';
import { FUEL_COLORS, FUEL_LABELS, getFuelPrice, getPriceColor } from '../utils/fuel';
import { getBrandDisplay } from '../utils/brands';

// Fix default marker icons in bundled environments
import markerIcon2x from 'leaflet/dist/images/marker-icon-2x.png';
import markerIcon from 'leaflet/dist/images/marker-icon.png';
import markerShadow from 'leaflet/dist/images/marker-shadow.png';

L.Icon.Default.mergeOptions({
  iconRetinaUrl: markerIcon2x,
  iconUrl: markerIcon,
  shadowUrl: markerShadow,
});

interface StationWithDistance extends Station {
  distance: number;
}

type GetStationHistoryFn = (
  stationId: number,
  postalCode: string,
) => Promise<Record<string, [number, number][]> | null>;

interface Props {
  center: [number, number];
  zoom: number;
  bounds: L.LatLngBoundsExpression | null;
  stations: StationWithDistance[];
  selectedFuel: FuelType;
  selectedStationId: number | null;
  onStationSelect: (id: number | null) => void;
  getStationHistory?: GetStationHistoryFn;
  onVisibleBoundsChange: (bounds: L.LatLngBounds) => void;
  searchCenter: [number, number] | null;
  searchRadius: number;
  priceBounds: { pMin: number; pMax: number };
  hoveredStationId: number | null;
  onGeolocate?: () => void;
  geolocating?: boolean;
  hasPanel?: boolean;
  panelOpen?: boolean;
}

// Tile layer — OSM France for FR labels without API key. MapTiler
// dataviz-light remains as an opt-in upgrade when VITE_MAPTILER_KEY is set
// (more modern minimal aesthetic, but requires account + secret + dashboard
// config). Default = OSM France because zero-setup and labels FR.
function BaseTileLayer() {
  const maptilerKey = import.meta.env.VITE_MAPTILER_KEY;
  const [useFallback, setUseFallback] = useState(false);

  if (!maptilerKey || useFallback) {
    return (
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> &copy; <a href="https://www.openstreetmap.fr/">OSM France</a>'
        url="https://{s}.tile.openstreetmap.fr/osmfr/{z}/{x}/{y}.png"
      />
    );
  }

  return (
    <TileLayer
      attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> &copy; <a href="https://www.maptiler.com/">MapTiler</a>'
      url={`https://api.maptiler.com/maps/dataviz-light/{z}/{x}/{y}.png?key=${maptilerKey}&lang=fr`}
      eventHandlers={{ tileerror: () => setUseFallback(true) }}
    />
  );
}

function createPriceIcon(price: number, color: string, dimmed = false): L.DivIcon {
  const label = price.toFixed(3).replace('.', ',').slice(0, -1); // "1,72" (2 decimals)
  const bg = dimmed ? 'var(--color-ink-muted)' : color;
  const opacity = dimmed ? '0.5' : '1';
  return L.divIcon({
    html: `<div style="
      background: ${bg};
      color: white;
      font-size: 11px;
      font-weight: 700;
      font-family: var(--font-sans);
      padding: 3px 6px;
      border-radius: 10px;
      border: 2px solid white;
      box-shadow: 0 2px 6px rgba(0,0,0,0.35);
      white-space: nowrap;
      line-height: 1;
      text-align: center;
      opacity: ${opacity};
      transition: opacity 0.15s;
    ">${label}</div>`,
    className: '',
    iconSize: [46, 22],
    iconAnchor: [23, 11],
    popupAnchor: [0, -13],
  });
}

function MapUpdater({
  center,
  zoom,
  bounds,
}: {
  center: [number, number];
  zoom: number;
  bounds: L.LatLngBoundsExpression | null;
}) {
  const map = useMap();
  const prevBounds = useRef(bounds);
  const prevCenter = useRef(center);

  useEffect(() => {
    if (bounds && bounds !== prevBounds.current) {
      // Desktop: pad right for panel (288px = w-72). Mobile: pad top for header/search, bottom for sheet.
      const isDesktop = window.innerWidth >= 768;
      map.flyToBounds(bounds, {
        duration: 1.2,
        paddingTopLeft: isDesktop ? [0, 0] : [20, 180],
        paddingBottomRight: isDesktop ? [288, 0] : [20, 80],
      });
      prevBounds.current = bounds;
      prevCenter.current = center;
    } else if (
      prevCenter.current[0] !== center[0] ||
      prevCenter.current[1] !== center[1]
    ) {
      map.flyTo(center, zoom, { duration: 1.2 });
      prevCenter.current = center;
    }
  }, [map, center, zoom, bounds]);

  return null;
}

function MarkerClusterGroup({
  stations,
  selectedFuel,
  selectedStationId,
  onStationSelect,
  getStationHistory,
  priceBounds,
  hoveredStationId,
}: {
  stations: StationWithDistance[];
  selectedFuel: FuelType;
  selectedStationId: number | null;
  onStationSelect: (id: number | null) => void;
  getStationHistory?: GetStationHistoryFn;
  priceBounds: { pMin: number; pMax: number };
  hoveredStationId: number | null;
}) {
  const map = useMap();
  const clusterRef = useRef<L.MarkerClusterGroup | null>(null);
  const markersRef = useRef<Map<number, L.Marker>>(new Map());
  const pricesRef = useRef<Map<number, number>>(new Map());
  const hoveredRef = useRef<number | null>(hoveredStationId);

  const stationData = useMemo(
    () => stations.filter((s) => getFuelPrice(s, selectedFuel) !== null),
    [stations, selectedFuel],
  );

  useEffect(() => {
    if (clusterRef.current) {
      map.removeLayer(clusterRef.current);
    }

    const newMarkers = new Map<number, L.Marker>();
    const newPrices = new Map<number, number>();

    const { pMin: minPrice, pMax: maxPrice } = priceBounds;

    const cluster = L.markerClusterGroup({
      chunkedLoading: true,
      maxClusterRadius: 50,
      spiderfyOnMaxZoom: true,
      showCoverageOnHover: false,
      disableClusteringAtZoom: 16,
      iconCreateFunction(c) {
        const childMarkers = c.getAllChildMarkers();
        const clusterPrices = childMarkers.map(m => (m.options as Record<string, unknown>).fuelPrice as number);
        const cheapest = Math.min(...clusterPrices);
        const count = c.getChildCount();
        const color = getPriceColor(cheapest, minPrice, maxPrice);
        const label = cheapest.toFixed(3).replace('.', ',').slice(0, -1);

        // Check if this cluster contains the hovered station
        const hovered = hoveredRef.current;
        const dimmed = hovered !== null && !childMarkers.some(
          m => (m.options as Record<string, unknown>).stationId === hovered,
        );
        const bg = dimmed ? 'var(--color-ink-muted)' : color;
        const opacity = dimmed ? '0.5' : '1';

        return L.divIcon({
          html: `<div style="
            position: relative;
            background: ${bg};
            padding: 3px 8px;
            border-radius: 12px;
            border: 2px solid white;
            box-shadow: 0 2px 6px rgba(0,0,0,0.35);
            color: white;
            font-weight: 700;
            font-size: 11px;
            font-family: var(--font-sans);
            white-space: nowrap;
            line-height: 1;
            text-align: center;
            opacity: ${opacity};
            transition: opacity 0.15s, background 0.15s;
          ">${label}<span style="
            position: absolute;
            top: -7px;
            right: -7px;
            background: var(--color-ink);
            color: white;
            font-size: 9px;
            font-weight: 700;
            min-width: 16px;
            height: 16px;
            border-radius: 8px;
            display: flex;
            align-items: center;
            justify-content: center;
            padding: 0 3px;
            border: 1.5px solid white;
            line-height: 1;
          ">${count}</span></div>`,
          className: '',
          iconSize: [52, 24],
          iconAnchor: [26, 12],
        });
      },
    });

    for (const s of stationData) {
      const price = getFuelPrice(s, selectedFuel)!;
      const color = getPriceColor(price, minPrice, maxPrice);
      const icon = createPriceIcon(price, color);
      const marker = L.marker([s.lat, s.lng], { icon, fuelPrice: price, stationId: s.id } as L.MarkerOptions);

      const popupContent = document.createElement('div');
      popupContent.innerHTML = renderPopupHTML(s, selectedFuel);
      marker.bindPopup(popupContent, { maxWidth: 280 });

      // Lazy-load evolution indicators on popup open
      if (getStationHistory) {
        let historyLoaded = false;
        marker.on('popupopen', async () => {
          if (historyLoaded) return;
          historyLoaded = true;
          const history = await getStationHistory(s.id, s.cp);
          injectVariationIndicators(popupContent, s, history);
          // Update popup size after content change
          marker.getPopup()?.update();
        });
      }

      marker.on('click', () => {
        onStationSelect(s.id);
      });

      newMarkers.set(s.id, marker);
      newPrices.set(s.id, price);
      cluster.addLayer(marker);
    }

    markersRef.current = newMarkers;
    pricesRef.current = newPrices;
    clusterRef.current = cluster;
    map.addLayer(cluster);

    return () => {
      if (clusterRef.current) {
        map.removeLayer(clusterRef.current);
      }
    };
  }, [map, stationData, selectedFuel, onStationSelect, getStationHistory, priceBounds]);

  // Open popup when station selected from panel
  useEffect(() => {
    if (selectedStationId && markersRef.current.has(selectedStationId)) {
      const marker = markersRef.current.get(selectedStationId)!;
      const cluster = clusterRef.current;
      if (cluster) {
        cluster.zoomToShowLayer(marker, () => {
          marker.openPopup();
        });
      }
    }
  }, [selectedStationId]);

  // Dim non-hovered markers and clusters when a station is hovered from the panel
  useEffect(() => {
    hoveredRef.current = hoveredStationId;
    const { pMin: minPrice, pMax: maxPrice } = priceBounds;
    for (const [id, marker] of markersRef.current) {
      const price = pricesRef.current.get(id);
      if (price == null) continue;
      if (hoveredStationId === null) {
        const color = getPriceColor(price, minPrice, maxPrice);
        marker.setIcon(createPriceIcon(price, color));
      } else {
        const dimmed = id !== hoveredStationId;
        const color = getPriceColor(price, minPrice, maxPrice);
        marker.setIcon(createPriceIcon(price, color, dimmed));
      }
    }
    // Refresh cluster icons so they also reflect the hover state
    if (clusterRef.current) {
      clusterRef.current.refreshClusters();
    }
  }, [hoveredStationId, priceBounds]);

  return null;
}

/**
 * Compute variation indicator HTML for a single fuel.
 * Returns { recent, yearly } HTML strings, or null if no data.
 */
function computeVariationHTML(
  currentPrice: number,
  fuelHistory: [number, number][] | undefined,
): { recent: string; yearly: string } | null {
  if (!fuelHistory || fuelHistory.length < 2) return null;

  const [prevEpoch, prevPrice] = fuelHistory[fuelHistory.length - 1];

  // Find first data point in January of current year
  const currentYear = new Date().getFullYear();
  const jan1Epoch = new Date(currentYear, 0, 1).getTime();
  const janEntry = fuelHistory.find(([epoch]) => epoch >= jan1Epoch);

  function formatVar(change: number, refLabel: string): string {
    const absChange = Math.abs(change);
    if (absChange < 0.002) {
      return `<span style="color:var(--color-ink-muted);font-size:10px;">\u2192 stable ${refLabel}</span>`;
    }
    const isUp = change > 0;
    const color = isUp ? 'var(--color-alert)' : 'var(--color-success)';
    const arrow = isUp ? '\u2197' : '\u2198';
    const sign = isUp ? '+' : '';
    const formatted = sign + change.toFixed(3).replace('.', ',');
    return `<span style="color:${color};font-size:10px;font-weight:600;">${arrow} ${formatted}\u20AC ${refLabel}</span>`;
  }

  const recentChange = Math.round((currentPrice - prevPrice) * 1000) / 1000;
  const prevDate = new Date(prevEpoch);
  const recentLabel = `(${prevDate.toLocaleDateString('fr-FR', { day: 'numeric', month: 'short' })})`;

  if (!janEntry) {
    return { recent: formatVar(recentChange, recentLabel), yearly: '' };
  }

  const [, janPrice] = janEntry;
  const yearlyChange = Math.round((currentPrice - janPrice) * 1000) / 1000;
  const yearlyPct = ((currentPrice - janPrice) / janPrice * 100).toFixed(1);
  const yearlyLabel = `dep. janv. (${yearlyPct}%)`;

  return {
    recent: formatVar(recentChange, recentLabel),
    yearly: formatVar(yearlyChange, yearlyLabel),
  };
}

/**
 * After history is loaded, inject variation indicators into existing popup DOM.
 */
function injectVariationIndicators(
  popupEl: HTMLElement,
  station: StationWithDistance,
  history: Record<string, [number, number][]> | null,
) {
  const slots = popupEl.querySelectorAll('[data-fuel-var]');
  slots.forEach((slot) => {
    const fuel = slot.getAttribute('data-fuel-var');
    if (!fuel) return;

    const fuelInfo = station.fuels[fuel as FuelType];
    if (!fuelInfo) return;

    const variation = computeVariationHTML(fuelInfo.p, history?.[fuel]);
    if (variation) {
      slot.innerHTML = `${variation.recent} · ${variation.yearly}`;
    } else {
      slot.innerHTML = '';
    }
  });
}

function renderPopupHTML(
  station: StationWithDistance,
  selectedFuel: FuelType,
): string {
  const fuels = Object.entries(station.fuels)
    .sort(([a], [b]) => {
      if (a === selectedFuel) return -1;
      if (b === selectedFuel) return 1;
      return 0;
    })
    .map(([fuel, info]) => {
      const color = FUEL_COLORS[fuel as FuelType];
      const price = info!.p.toFixed(3).replace('.', ',');

      // Stale indicator (R9): if last update > 72h ago, show warning icon
      // + tooltip + faded price color. The popup runs in raw HTML so the
      // tooltip uses the native `title` attribute (no custom UI lib).
      const ageMs = Date.now() - new Date(info!.d).getTime();
      const stale = ageMs > 72 * 60 * 60 * 1000;
      const hoursAgo = Math.round(ageMs / (60 * 60 * 1000));
      const priceColor = stale ? 'var(--color-ink-muted)' : 'var(--color-ink)';
      const staleIcon = stale
        ? `<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="var(--color-alert)" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" style="flex-shrink:0;" aria-label="Donn\u00E9e non rafra\u00EEchie depuis ${hoursAgo} h"><title>Donn\u00E9e non rafra\u00EEchie depuis ${hoursAgo} h</title><path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>`
        : '';

      return `<div style="padding:4px 0;border-bottom:1px solid color-mix(in srgb, var(--color-ink-muted) 20%, transparent);">
        <div style="display:flex;align-items:center;justify-content:space-between;gap:8px;">
          <span style="display:flex;align-items:center;gap:6px;">
            <span style="width:8px;height:8px;border-radius:50%;background:${color};display:inline-block;"></span>
            <span style="font-size:13px;color:var(--color-ink);">${FUEL_LABELS[fuel as FuelType]}</span>
          </span>
          <span style="display:flex;align-items:center;gap:5px;">
            ${staleIcon}
            <span style="font-size:13px;font-weight:600;color:${priceColor};">${price} \u20AC</span>
          </span>
        </div>
        <div data-fuel-var="${fuel}" style="padding-left:14px;min-height:14px;">
          <span style="color:var(--color-ink-muted);font-size:10px;">...</span>
        </div>
      </div>`;
    })
    .join('');

  const distStr =
    station.distance < 1
      ? `${Math.round(station.distance * 1000)} m`
      : `${station.distance.toFixed(1)} km`;

  const destLabel = station.brand
    ? `${station.brand}, ${station.addr}, ${station.cp} ${station.city}`
    : `${station.addr}, ${station.cp} ${station.city}`;
  const destination = encodeURIComponent(destLabel);

  const ua = navigator.userAgent;
  const isIOS = /iPad|iPhone|iPod/.test(ua);
  const isAndroid = /Android/.test(ua);

  let navUrl: string;
  if (isAndroid || isIOS) {
    navUrl = `geo:${station.lat},${station.lng}?q=${station.lat},${station.lng}(${destination})`;
  } else {
    navUrl = `https://www.google.com/maps/dir/?api=1&destination=${destination}`;
  }

  const brandHTML = station.brand
    ? (() => {
        const { abbr, color } = getBrandDisplay(station.brand);
        return `<div style="display:flex;align-items:center;gap:6px;margin-bottom:6px;">
          <span style="background:${color};color:white;font-size:10px;font-weight:700;padding:2px 5px;border-radius:4px;line-height:1;">${abbr}</span>
          <span style="font-size:12px;font-weight:500;color:var(--color-ink-muted);">${station.brand}</span>
        </div>`;
      })()
    : '';

  return `
    <div style="min-width:200px;font-family:var(--font-sans);">
      ${brandHTML}
      <div style="font-weight:600;font-size:13px;color:var(--color-ink);margin-bottom:2px;">${station.addr}</div>
      <div style="font-size:11px;color:var(--color-ink-muted);margin-bottom:8px;">${station.city} \u00b7 ${station.cp} \u00b7 ${distStr}</div>
      ${fuels}
      <a href="${navUrl}" target="_blank" rel="noopener noreferrer"
         style="display:flex;align-items:center;justify-content:center;gap:6px;margin-top:10px;padding:7px 0;background:var(--color-primary);color:white;border-radius:8px;font-size:12px;font-weight:600;text-decoration:none;cursor:pointer;">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <polygon points="3 11 22 2 13 21 11 13 3 11"></polygon>
        </svg>
        Itin\u00e9raire
      </a>
    </div>
  `;
}

function SearchRadiusCircle({
  center,
  radiusKm,
}: {
  center: [number, number] | null;
  radiusKm: number;
}) {
  const map = useMap();
  const circleRef = useRef<L.Circle | null>(null);
  const markerRef = useRef<L.CircleMarker | null>(null);

  useEffect(() => {
    // Clean previous
    if (circleRef.current) {
      map.removeLayer(circleRef.current);
      circleRef.current = null;
    }
    if (markerRef.current) {
      map.removeLayer(markerRef.current);
      markerRef.current = null;
    }

    if (!center) return;

    // Radius circle
    const circle = L.circle(center, {
      radius: radiusKm * 1000,
      color: '#171717',
      weight: 2,
      opacity: 0.4,
      fillColor: '#171717',
      fillOpacity: 0.06,
      interactive: false,
    });
    circle.addTo(map);
    circleRef.current = circle;

    // Center dot
    const dot = L.circleMarker(center, {
      radius: 7,
      color: '#171717',
      weight: 3,
      opacity: 0.9,
      fillColor: '#ffffff',
      fillOpacity: 1,
      interactive: false,
    });
    dot.addTo(map);
    markerRef.current = dot;

    return () => {
      if (circleRef.current) map.removeLayer(circleRef.current);
      if (markerRef.current) map.removeLayer(markerRef.current);
    };
  }, [map, center, radiusKm]);

  return null;
}

function BoundsTracker({ onChange }: { onChange: (bounds: L.LatLngBounds) => void }) {
  const map = useMap();
  const onChangeRef = useRef(onChange);

  useEffect(() => {
    onChangeRef.current = onChange;
  }, [onChange]);

  useEffect(() => {
    const handler = () => {
      onChangeRef.current(map.getBounds());
    };
    // Emit initial bounds
    handler();
    map.on('moveend', handler);
    map.on('zoomend', handler);
    return () => {
      map.off('moveend', handler);
      map.off('zoomend', handler);
    };
  }, [map]);

  return null;
}

export function MapView({
  center,
  zoom,
  bounds,
  stations,
  selectedFuel,
  selectedStationId,
  onStationSelect,
  getStationHistory,
  onVisibleBoundsChange,
  searchCenter,
  searchRadius,
  priceBounds,
  hoveredStationId,
  onGeolocate,
  geolocating,
  hasPanel,
  panelOpen,
}: Props) {
  return (
    <>
      <MapContainer
        center={center}
        zoom={zoom}
        className="h-full w-full"
        zoomControl={false}
        minZoom={5}
        maxBounds={[
          [41.2, -5.5],
          [51.5, 10],
        ]}
        maxBoundsViscosity={1.0}
      >
        <BaseTileLayer />
        <MapUpdater center={center} zoom={zoom} bounds={bounds} />
        <BoundsTracker onChange={onVisibleBoundsChange} />
        <SearchRadiusCircle center={searchCenter} radiusKm={searchRadius} />
        <MarkerClusterGroup
          stations={stations}
          selectedFuel={selectedFuel}
          selectedStationId={selectedStationId}
          onStationSelect={onStationSelect}
          getStationHistory={getStationHistory}
          priceBounds={priceBounds}
          hoveredStationId={hoveredStationId}
        />
      </MapContainer>

      {/* Geolocate button — bottom-right, Leaflet-style */}
      {onGeolocate && (
        <button
          onClick={onGeolocate}
          disabled={geolocating}
          aria-label={geolocating ? 'Localisation en cours' : 'Me localiser'}
          className={`absolute right-3 z-10 flex h-10 w-10 items-center justify-center rounded-full bg-white shadow-lg ring-1 ring-gray-200 transition-all hover:bg-gray-50 disabled:opacity-60 md:bottom-6 ${hasPanel ? `${panelOpen ? 'bottom-[340px]' : 'bottom-16'} md:right-[300px]` : 'bottom-20'}`}
          title="Me localiser"
        >
          {geolocating ? (
            <svg className="h-5 w-5 animate-spin text-primary" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
            </svg>
          ) : (
            <svg className="h-5 w-5 text-gray-700" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <circle cx="12" cy="12" r="3" />
              <path d="M12 2v4M12 18v4M2 12h4M18 12h4" />
            </svg>
          )}
        </button>
      )}
    </>
  );
}
