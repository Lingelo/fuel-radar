import { useEffect, useMemo, useRef, useState } from 'react';
import L from 'leaflet';
import 'leaflet.markercluster';
import 'leaflet.markercluster/dist/MarkerCluster.css';
import 'leaflet.markercluster/dist/MarkerCluster.Default.css';
import { MapContainer, TileLayer, useMap } from 'react-leaflet';
import { useFilters } from '../state/FiltersContext';
import { useViewNav } from '../state/ViewContext';
import { useFavorites } from '../state/FavoritesContext';
import { useNearbyStations } from '../hooks/useNearbyStations';
import { haversineKm, formatDistance } from '../lib/distance';
import { timeAgo } from '../lib/data';
import { formatPrice } from '../lib/format';
import { getBrowserLocation } from '../lib/geocode';
import { getPriceBounds, getPriceColor } from '../lib/priceColor';
import { SearchBar } from '../components/SearchBar';
import { FilterSheet } from '../components/FilterSheet';
import { StationPopup } from '../components/StationPopup';
import { Icon } from '../components/Icon';
import { FUEL_TYPES, type Station } from '../types';

interface PricedStation {
  station: Station;
  price: number;
  distance: number;
  color: string;
}

function makePricePin(price: number, color: string, selected = false) {
  const label = formatPrice(price);
  const wrapClass = selected ? 'fuel-pin fuel-pin-selected' : 'fuel-pin';
  const pulse = selected ? '<div class="fuel-pin-pulse"></div>' : '';
  return L.divIcon({
    html: `
      <div class="${wrapClass}" style="position:relative;display:flex;flex-direction:column;align-items:center;">
        ${pulse}
        <div class="fuel-pin-body" style="background:${color};color:#fff;padding:3px 7px;border-radius:8px;border:2px solid white;font-weight:700;font-size:12px;line-height:1;box-shadow:0 2px 6px rgba(0,0,0,0.35);white-space:nowrap;transition:transform 200ms cubic-bezier(0.34,1.56,0.64,1),box-shadow 200ms ease;">${label} €</div>
        <div style="width:0;height:0;border-left:6px solid transparent;border-right:6px solid transparent;border-top:7px solid ${color};margin-top:-1px;"></div>
      </div>
    `,
    className: '',
    iconSize: [60, 28],
    iconAnchor: [30, 28],
  });
}

const userIcon = L.divIcon({
  html: `<div style="width:18px;height:18px;border-radius:50%;background:#0c4a6e;border:3px solid white;box-shadow:0 2px 6px rgba(0,0,0,0.4);"></div>`,
  className: '',
  iconSize: [18, 18],
  iconAnchor: [9, 9],
});

function MapRecenter({ lat, lng }: { lat: number; lng: number }) {
  const map = useMap();
  useEffect(() => {
    // Always animate to the new position. The reference-equality guard
    // we had previously caused the second search in a row to silently
    // no-op when the user picked the same coords pair twice.
    map.flyTo([lat, lng], Math.max(13, map.getZoom()), { duration: 0.6 });
  }, [lat, lng, map]);
  return null;
}

/** Pans/zooms the map to a station when its id changes. */
function PanToStation({
  station,
}: {
  station: { lat: number; lng: number } | null;
}) {
  const map = useMap();
  useEffect(() => {
    if (!station) return;
    map.flyTo([station.lat, station.lng], Math.max(15, map.getZoom()), {
      duration: 0.6,
    });
  }, [station, map]);
  return null;
}

function SearchRadiusCircle({ lat, lng, radiusKm }: { lat: number; lng: number; radiusKm: number }) {
  const map = useMap();
  const circleRef = useRef<L.Circle | null>(null);
  const markerRef = useRef<L.Marker | null>(null);

  useEffect(() => {
    if (circleRef.current) {
      map.removeLayer(circleRef.current);
      circleRef.current = null;
    }
    if (markerRef.current) {
      map.removeLayer(markerRef.current);
      markerRef.current = null;
    }
    const circle = L.circle([lat, lng], {
      radius: radiusKm * 1000,
      color: '#a33900',
      weight: 2,
      opacity: 0.5,
      fillColor: '#a33900',
      fillOpacity: 0.06,
      interactive: false,
    });
    circle.addTo(map);
    circleRef.current = circle;

    const marker = L.marker([lat, lng], { icon: userIcon, interactive: false });
    marker.addTo(map);
    markerRef.current = marker;

    return () => {
      if (circleRef.current) map.removeLayer(circleRef.current);
      if (markerRef.current) map.removeLayer(markerRef.current);
    };
  }, [map, lat, lng, radiusKm]);

  return null;
}

function BoundsTracker({ onChange }: { onChange: (b: L.LatLngBounds) => void }) {
  const map = useMap();
  const ref = useRef(onChange);
  useEffect(() => {
    ref.current = onChange;
  }, [onChange]);
  useEffect(() => {
    const handler = () => ref.current(map.getBounds());
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

function StationsCluster({
  priced,
  selectedId,
  onSelect,
}: {
  priced: PricedStation[];
  selectedId: number | null;
  onSelect: (id: number) => void;
}) {
  const map = useMap();
  const clusterRef = useRef<L.MarkerClusterGroup | null>(null);
  const markersRef = useRef<Map<number, L.Marker>>(new Map());
  const dataRef = useRef<Map<number, { price: number; color: string }>>(new Map());

  useEffect(() => {
    if (clusterRef.current) {
      map.removeLayer(clusterRef.current);
    }
    const cluster = L.markerClusterGroup({
      chunkedLoading: true,
      maxClusterRadius: 50,
      showCoverageOnHover: false,
      disableClusteringAtZoom: 15,
      iconCreateFunction(c) {
        const children = c.getAllChildMarkers();
        const datas = children.map((m) => m.options as Record<string, unknown>);
        const minPrice = Math.min(...datas.map((d) => d.fuelPrice as number));
        const minColor = (datas.find((d) => d.fuelPrice === minPrice)?.fuelColor as string) ?? '#006399';
        const count = c.getChildCount();
        return L.divIcon({
          html: `
            <div style="position:relative;background:${minColor};color:#fff;padding:3px 8px;border-radius:12px;border:2px solid white;box-shadow:0 2px 6px rgba(0,0,0,0.35);font-weight:700;font-size:12px;line-height:1;white-space:nowrap;">
              ${formatPrice(minPrice)} €
              <span style="position:absolute;top:-7px;right:-7px;background:#141b2b;color:white;font-size:10px;font-weight:700;min-width:18px;height:18px;border-radius:9px;display:flex;align-items:center;justify-content:center;padding:0 4px;border:1.5px solid white;">${count}</span>
            </div>`,
          className: '',
          iconSize: [60, 28],
          iconAnchor: [30, 14],
        });
      },
    });

    const newMarkers = new Map<number, L.Marker>();
    const newData = new Map<number, { price: number; color: string }>();
    for (const { station, price, color } of priced) {
      const icon = makePricePin(price, color, station.id === selectedId);
      const marker = L.marker([station.lat, station.lng], {
        icon,
        fuelPrice: price,
        fuelColor: color,
      } as L.MarkerOptions);
      marker.on('click', () => onSelect(station.id));
      cluster.addLayer(marker);
      newMarkers.set(station.id, marker);
      newData.set(station.id, { price, color });
    }
    markersRef.current = newMarkers;
    dataRef.current = newData;

    clusterRef.current = cluster;
    map.addLayer(cluster);

    return () => {
      if (clusterRef.current) {
        map.removeLayer(clusterRef.current);
      }
    };
    // We deliberately depend only on `priced` and `onSelect` — selection
    // changes are handled below without rebuilding the entire cluster.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [map, priced, onSelect]);

  // Update only the selected/deselected markers without rebuilding the cluster.
  useEffect(() => {
    for (const [id, marker] of markersRef.current) {
      const data = dataRef.current.get(id);
      if (!data) continue;
      marker.setIcon(makePricePin(data.price, data.color, id === selectedId));
    }
  }, [selectedId]);

  return null;
}

export function MapScreen() {
  const f = useFilters();
  const nav = useViewNav();
  const fav = useFavorites();
  const { stations, loading } = useNearbyStations();
  const [filterOpen, setFilterOpen] = useState(false);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [popupId, setPopupId] = useState<number | null>(null);
  const [bounds, setBounds] = useState<L.LatLngBounds | null>(null);
  const [panTarget, setPanTarget] = useState<{ lat: number; lng: number; key: number } | null>(null);
  const [panelOpen, setPanelOpen] = useState(true);
  /** 'hidden' = sheet is fully tucked away (default; small pill to reopen).
   *  'collapsed' = ~200 px peek with horizontal carousel.
   *  'expanded'  = ~60vh full vertical scrollable list. */
  const [sheetState, setSheetState] = useState<'hidden' | 'collapsed' | 'expanded'>('hidden');
  const sheetExpanded = sheetState === 'expanded';
  const sheetHidden = sheetState === 'hidden';
  const [pendingFocusId, setPendingFocusId] = useState<number | null>(null);
  const sidePanelRef = useRef<HTMLDivElement>(null);

  // On mount, pick up any focus-station id passed by the previous screen.
  useEffect(() => {
    const id = nav.consumeFocusStation();
    if (id !== null) setPendingFocusId(id);
    // Run once when MapScreen mounts.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Once stations finish loading, fly to the requested focus station.
  useEffect(() => {
    if (pendingFocusId === null) return;
    const match = stations.find((s) => s.id === pendingFocusId);
    if (!match) return;
    setSelectedId(match.id);
    setPanTarget({ lat: match.lat, lng: match.lng, key: Date.now() });
    setPendingFocusId(null);
  }, [pendingFocusId, stations]);
  const carouselRef = useRef<HTMLDivElement>(null);

  // Stations with selected fuel + brand filter, with color tier by price
  const priced: PricedStation[] = useMemo(() => {
    if (!f.userLocation) return [];
    const filtered = stations
      .filter((s) => s.fuels[f.selectedFuel])
      .filter((s) => f.selectedBrands.size === 0 || (s.brand && f.selectedBrands.has(s.brand)));
    const prices = filtered.map((s) => s.fuels[f.selectedFuel]!.p);
    const { pMin, pMax } = getPriceBounds(prices);
    return filtered
      .map((s) => {
        const price = s.fuels[f.selectedFuel]!.p;
        return {
          station: s,
          price,
          color: getPriceColor(price, pMin, pMax),
          distance: haversineKm(f.userLocation!.lat, f.userLocation!.lng, s.lat, s.lng),
        };
      })
      .sort((a, b) => a.price - b.price);
  }, [stations, f.selectedFuel, f.selectedBrands, f.userLocation]);

  // Stations in the visible map viewport. Distance stays anchored to the
  // user's position — pan/zoom only filters which stations show in the list,
  // it never alters their distance to the user.
  const visible = useMemo(() => {
    if (!bounds) return priced;
    return priced.filter((p) =>
      bounds.contains([p.station.lat, p.station.lng] as L.LatLngTuple),
    );
  }, [priced, bounds]);

  useEffect(() => {
    if (visible.length > 0 && (selectedId === null || !visible.some((p) => p.station.id === selectedId))) {
      setSelectedId(visible[0].station.id);
    }
  }, [visible, selectedId]);

  // When the user-driven inputs change OR the visible viewport changes
  // (zoom/pan — `bounds` only updates on moveend/zoomend, never mid-drag),
  // reset both scroll containers to the start so the cheapest in the
  // current view is always the first card the user sees.
  useEffect(() => {
    if (carouselRef.current) {
      carouselRef.current.scrollTo({ left: 0, top: 0, behavior: 'smooth' });
    }
    if (sidePanelRef.current) {
      sidePanelRef.current.scrollTo({ top: 0, behavior: 'smooth' });
    }
  }, [f.selectedFuel, f.selectedBrands, f.radiusKm, f.userLocation, bounds]);

  const onLocateMe = async () => {
    const c = await getBrowserLocation();
    if (c) f.setUserLocation(c);
  };

  const popupStation = popupId !== null ? priced.find((p) => p.station.id === popupId) : null;

  if (!f.userLocation) {
    return (
      <div className="h-full flex items-center justify-center text-on-surface-variant">
        <p>Localisation…</p>
      </div>
    );
  }

  return (
    <div className="h-full relative">
      <MapContainer
        center={[f.userLocation.lat, f.userLocation.lng]}
        zoom={13}
        scrollWheelZoom
        zoomControl={false}
        className="absolute inset-0 z-0"
      >
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> &copy; <a href="https://carto.com/">CARTO</a>'
          url="https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png"
        />
        <MapRecenter lat={f.userLocation.lat} lng={f.userLocation.lng} />
        <SearchRadiusCircle
          lat={f.userLocation.lat}
          lng={f.userLocation.lng}
          radiusKm={f.radiusKm}
        />
        <BoundsTracker onChange={setBounds} />
        <PanToStation station={panTarget} />
        <StationsCluster
          priced={priced}
          selectedId={selectedId}
          onSelect={(id) => {
            setSelectedId(id);
            setPopupId(id);
            const el = carouselRef.current?.querySelector(`[data-station="${id}"]`);
            if (el) (el as HTMLElement).scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' });
          }}
        />
      </MapContainer>

      {/* Search overlay */}
      <div className="absolute top-md left-container-margin right-container-margin md:w-[420px] md:left-4 md:right-auto z-[400] flex flex-col gap-2">
        <SearchBar
          initialLabel={f.searchLabel}
          onResult={(r) => {
            f.setUserLocation({ lat: r.lat, lng: r.lng });
            f.setSearchLabel(`${r.postcode} ${r.city}`);
            setSelectedId(null);
          }}
          onOpenFilters={() => setFilterOpen(true)}
        />
        {(() => {
          const QUICK: typeof FUEL_TYPES = ['Gazole', 'E10', 'SP98'];
          const quickList = QUICK.includes(f.selectedFuel)
            ? QUICK
            : [f.selectedFuel, ...QUICK];
          return (
            <div className="flex gap-2 flex-wrap">
              {quickList.map((fuel) => {
                const active = f.selectedFuel === fuel;
                return (
                  <button
                    key={fuel}
                    onClick={() => f.setSelectedFuel(fuel)}
                    className={[
                      'px-3 py-1 rounded-full text-label-caps font-bold tracking-wider whitespace-nowrap transition-colors active:scale-95 shadow-sm',
                      active
                        ? 'bg-secondary text-on-secondary'
                        : 'bg-surface-container-lowest text-on-surface border border-outline-variant hover:border-secondary',
                    ].join(' ')}
                  >
                    {fuel}
                  </button>
                );
              })}
              <span className="bg-surface-container-lowest text-on-surface border border-outline-variant px-3 py-1 rounded-full text-label-caps font-bold tracking-wider whitespace-nowrap">
                {f.radiusKm} km
              </span>
              {f.selectedBrands.size > 0 && (
                <span className="bg-surface-container-lowest text-on-surface border border-outline-variant px-3 py-1 rounded-full text-label-caps font-bold tracking-wider whitespace-nowrap">
                  {f.selectedBrands.size} enseigne{f.selectedBrands.size > 1 ? 's' : ''}
                </span>
              )}
            </div>
          );
        })()}
      </div>

      {loading && (
        <div className="absolute top-32 md:top-24 left-1/2 -translate-x-1/2 z-[400] bg-surface-container-lowest text-on-surface px-4 py-1 rounded-full shadow-md text-body-sm border border-outline-variant">
          Chargement des stations…
        </div>
      )}

      {!loading && stations.length > 0 && priced.length === 0 && (
        <div className="absolute top-32 md:top-24 left-1/2 -translate-x-1/2 z-[400] bg-surface-container-lowest text-on-surface px-4 py-2 rounded-full shadow-md text-body-sm border border-outline-variant">
          Aucune station avec {f.selectedFuel} dans ce rayon.
        </div>
      )}

      <button
        onClick={onLocateMe}
        className={[
          'absolute right-4 md:bottom-6 bg-surface-container-lowest text-primary p-md rounded-full shadow-[0_4px_12px_rgba(20,27,43,0.15)] active:scale-95 transition-all z-[400] flex items-center justify-center border border-outline-variant',
          sheetExpanded ? 'bottom-[60vh]' : sheetHidden ? 'bottom-24' : 'bottom-48',
          panelOpen ? 'md:right-[396px]' : 'md:right-4',
        ].join(' ')}
        aria-label="Me localiser"
      >
        <Icon name="my_location" filled />
      </button>

      {/* Mobile: floating pill to re-open the sheet when fully hidden */}
      {sheetHidden && (
        <button
          onClick={() => setSheetState('collapsed')}
          className="md:hidden fixed bottom-20 left-1/2 -translate-x-1/2 z-[450] bg-primary text-on-primary rounded-full shadow-[0_4px_12px_rgba(20,27,43,0.25)] px-4 py-2.5 flex items-center gap-2 text-body-sm font-semibold active:scale-95 transition-transform"
          aria-label="Afficher la liste des stations"
        >
          <Icon name="expand_less" size={18} />
          Voir {visible.length} station{visible.length > 1 ? 's' : ''}
        </button>
      )}

      {/* Mobile: bottom sheet with drag handle, collapsible */}
      <div
        className={[
          'md:hidden fixed left-0 right-0 bottom-16 z-[450] bg-surface-container-lowest rounded-t-2xl shadow-[0_-8px_24px_rgba(20,27,43,0.18)] border-t border-outline-variant transition-[height] duration-300 ease-out flex flex-col overflow-hidden',
          sheetHidden ? 'h-0 border-t-0 shadow-none' : sheetExpanded ? 'h-[60vh]' : 'h-[200px]',
        ].join(' ')}
        aria-hidden={sheetHidden}
      >
        <div className="flex items-center justify-between gap-2 px-3 py-2 shrink-0 border-b border-surface-variant">
          <button
            onClick={() => setSheetState((s) => (s === 'expanded' ? 'collapsed' : 'expanded'))}
            className="flex-1 min-w-0 flex items-center gap-1.5 text-left active:scale-[0.98] transition-transform"
            aria-label={sheetExpanded ? 'Réduire la liste' : 'Étendre la liste'}
          >
            <Icon name={sheetExpanded ? 'expand_more' : 'expand_less'} size={20} className="text-primary shrink-0" />
            <span className="text-body-lg font-semibold text-on-surface truncate">
              {visible.length} station{visible.length > 1 ? 's' : ''}
            </span>
            <span className="text-body-sm text-primary truncate">
              {sheetExpanded ? 'Réduire' : 'Tout voir'}
            </span>
          </button>
          <button
            onClick={() => setSheetState('hidden')}
            className="text-body-sm font-semibold text-on-surface-variant px-2.5 py-1 rounded-lg hover:bg-surface-container active:scale-95 transition-transform shrink-0 flex items-center gap-1"
            aria-label="Masquer la liste"
          >
            <Icon name="close" size={16} />
            Masquer
          </button>
        </div>
        <div
          ref={carouselRef}
          className={[
            'flex-1 px-md pb-3 gap-gutter',
            sheetExpanded
              ? 'overflow-y-auto flex flex-col'
              : 'overflow-x-auto snap-x snap-mandatory flex no-scrollbar',
          ].join(' ')}
        >
          {visible.length === 0 && (
            <p className="text-center text-body-sm text-on-surface-variant py-lg w-full">
              Aucune station dans la zone visible.
            </p>
          )}
          {visible.slice(0, sheetExpanded ? 50 : 20).map(({ station, price, distance, color }, idx) => {
            const isCheapestVisible = idx === 0;
            const selected = selectedId === station.id;
            return (
              <button
                key={station.id}
                data-station={station.id}
                onClick={() => {
                  setSelectedId(station.id);
                  setPanTarget({ lat: station.lat, lng: station.lng, key: Date.now() });
                }}
                onDoubleClick={() => setPopupId(station.id)}
                className={[
                  sheetExpanded
                    ? 'w-full p-3 rounded-lg'
                    : 'snap-center shrink-0 w-[85%] max-w-[340px] p-md rounded-xl shadow-[0_4px_12px_rgba(20,27,43,0.1)]',
                  'bg-surface-container-lowest text-left transition-colors',
                  selected
                    ? 'border-2 border-primary'
                    : 'border border-outline-variant',
                ].join(' ')}
              >
                <div className="flex justify-between items-start gap-2">
                  <div className="min-w-0 flex-1">
                    <h3 className={`${sheetExpanded ? 'text-body-lg' : 'text-headline-md'} font-semibold text-on-surface truncate`}>
                      {station.brand ?? station.city}
                    </h3>
                    <p className="text-body-sm text-on-surface-variant flex items-center gap-1 truncate">
                      <Icon name="location_on" size={14} />
                      <span className="truncate">
                        {formatDistance(distance)}
                        {sheetExpanded && ` • ${station.addr || station.city}`}
                      </span>
                    </p>
                  </div>
                  <div className="text-right shrink-0">
                    <div
                      className={`${sheetExpanded ? 'text-headline-md' : 'text-display-price'} font-bold tracking-tight whitespace-nowrap`}
                      style={{ color }}
                    >
                      {formatPrice(price)}
                      <span className={sheetExpanded ? 'ml-0.5' : 'text-headline-md font-semibold ml-0.5'}>€</span>
                    </div>
                    {isCheapestVisible && (
                      <span className="inline-flex items-center gap-0.5 mt-0.5 bg-tertiary-container text-on-tertiary-container px-1.5 py-0.5 rounded-full text-[10px] font-bold tracking-wider">
                        <Icon name="trending_down" size={10} /> Min
                      </span>
                    )}
                  </div>
                </div>
                <div className="flex items-center justify-between mt-2 text-body-sm text-on-surface-variant">
                  <span>
                    {station.fuels[f.selectedFuel]
                      ? timeAgo(station.fuels[f.selectedFuel]!.d)
                      : '—'}
                  </span>
                  <span className="text-primary text-label-caps font-bold tracking-wider flex items-center gap-1">
                    {selected ? 'Centrée' : 'Centrer'} <Icon name="my_location" size={12} />
                  </span>
                </div>
              </button>
            );
          })}
        </div>
      </div>

      {/* Desktop: floating tab to re-open the panel when it's hidden */}
      {!panelOpen && (
        <button
          onClick={() => setPanelOpen(true)}
          className="hidden md:flex absolute top-32 right-0 z-[401] bg-primary text-on-primary rounded-l-full pl-3 pr-4 py-2.5 shadow-[0_4px_12px_rgba(20,27,43,0.25)] active:scale-95 transition-transform items-center gap-2 text-body-sm font-semibold"
          aria-label="Afficher la liste des stations"
        >
          <Icon name="chevron_left" size={18} />
          Voir {visible.length} station{visible.length > 1 ? 's' : ''}
        </button>
      )}

      {/* Desktop: right side panel with vertical scrollable list */}
      {panelOpen && (
        <aside className="hidden md:flex absolute top-32 right-4 bottom-4 w-[360px] flex-col bg-surface-container-lowest rounded-xl shadow-[0_4px_24px_rgba(20,27,43,0.12)] border border-outline-variant z-[400] overflow-hidden">
          <header className="flex items-start justify-between gap-2 px-4 py-3 border-b border-outline-variant shrink-0">
            <div className="min-w-0">
              <h2 className="text-headline-md font-semibold text-on-surface">
                {visible.length} station{visible.length > 1 ? 's' : ''}
              </h2>
              <p className="text-body-sm text-on-surface-variant">
                dans la zone visible
              </p>
            </div>
            <div className="flex items-start gap-2">
              <div className="text-right">
                <div className="text-label-caps font-bold tracking-wider text-on-surface-variant">
                  {f.selectedFuel}
                </div>
                <div className="text-body-sm text-primary">{f.radiusKm} km</div>
              </div>
              <button
                onClick={() => setPanelOpen(false)}
                className="p-1 rounded-full hover:bg-surface-container active:scale-95 transition-transform shrink-0"
                aria-label="Masquer la liste"
                title="Masquer la liste"
              >
                <Icon name="close" size={20} />
              </button>
            </div>
          </header>
          <div ref={sidePanelRef} className="overflow-y-auto flex-1 p-3 flex flex-col gap-2">
            {visible.length === 0 && (
              <p className="text-center text-body-sm text-on-surface-variant py-lg">
                Aucune station dans la zone visible. Déplace la carte ou élargis le rayon.
              </p>
            )}
            {visible.slice(0, 50).map(({ station, price, distance, color }, idx) => {
              const isCheapestVisible = idx === 0;
              const selected = selectedId === station.id;
              return (
                <button
                  key={station.id}
                  onClick={() => {
                    setSelectedId(station.id);
                    setPanTarget({ lat: station.lat, lng: station.lng, key: Date.now() });
                  }}
                  onDoubleClick={() => setPopupId(station.id)}
                  className={[
                    'w-full text-left p-3 rounded-lg transition-colors',
                    selected
                      ? 'border-2 border-primary bg-surface-container-low'
                      : 'border border-surface-variant bg-surface-container-lowest hover:border-primary',
                  ].join(' ')}
                >
                  <div className="flex justify-between items-start gap-2">
                    <div className="min-w-0 flex-1">
                      <h3 className="text-body-lg font-semibold text-on-surface truncate">
                        {station.brand ?? station.city}
                      </h3>
                      <p className="text-body-sm text-on-surface-variant flex items-center gap-1 truncate">
                        <Icon name="location_on" size={14} />
                        <span className="truncate">
                          {formatDistance(distance)} • {station.addr || station.city}
                        </span>
                      </p>
                    </div>
                    <div className="text-right shrink-0">
                      <div
                        className="text-headline-md font-bold tracking-tight whitespace-nowrap"
                        style={{ color }}
                      >
                        {formatPrice(price)} €
                      </div>
                      {isCheapestVisible && (
                        <span className="inline-flex items-center gap-0.5 mt-0.5 bg-tertiary-container text-on-tertiary-container px-1.5 py-0.5 rounded-full text-[10px] font-bold tracking-wider">
                          <Icon name="trending_down" size={10} /> Min
                        </span>
                      )}
                    </div>
                  </div>
                  <div className="flex items-center justify-between mt-2 text-body-sm text-on-surface-variant">
                    <span>
                      {station.fuels[f.selectedFuel]
                        ? `${timeAgo(station.fuels[f.selectedFuel]!.d)}`
                        : '—'}
                    </span>
                    <span className="text-primary text-label-caps font-bold tracking-wider flex items-center gap-1">
                      {selected ? 'Centrée' : 'Centrer'} <Icon name="my_location" size={12} />
                    </span>
                  </div>
                </button>
              );
            })}
          </div>
          <footer className="px-4 py-2 border-t border-outline-variant text-body-sm text-on-surface-variant shrink-0 text-center">
            Double-clic pour ouvrir la fiche
          </footer>
        </aside>
      )}

      {popupStation && (
        <StationPopup
          station={popupStation.station}
          distanceKm={popupStation.distance}
          selectedFuel={f.selectedFuel}
          referencePrices={priced.map((p) => p.price)}
          isFavorite={fav.isFavorite(popupStation.station.id)}
          onToggleFavorite={() => fav.toggle(popupStation.station.id)}
          onOpenDetails={() => {
            setPopupId(null);
            nav.goDetails(popupStation.station.id);
          }}
          onClose={() => setPopupId(null)}
        />
      )}

      <FilterSheet open={filterOpen} onClose={() => setFilterOpen(false)} />
    </div>
  );
}
