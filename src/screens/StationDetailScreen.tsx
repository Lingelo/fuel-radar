import { useEffect, useState } from 'react';
import L from 'leaflet';
import { MapContainer, Marker, TileLayer, useMap } from 'react-leaflet';
import { useFilters } from '../state/FiltersContext';
import { useViewNav } from '../state/ViewContext';
import { useFavorites } from '../state/FavoritesContext';
import { fetchDepartment, fetchDeptHistory, timeAgo } from '../lib/data';
import { deptsAround } from '../lib/deptIndex';
import { haversineKm, formatDistance } from '../lib/distance';
import { useI18n } from '../i18n';
import { Icon } from '../components/Icon';
import { PriceTrendBars } from '../components/PriceTrendBars';
import { formatPrice, formatPriceDelta } from '../lib/format';
import { getPriceBounds, getPriceColor } from '../lib/priceColor';
import { getServiceIcon } from '../lib/services';
import type { FuelType, Station } from '../types';
import { FUEL_TYPES } from '../types';

interface Props {
  stationId: number;
}

const detailPin = L.divIcon({
  html: `
    <div style="display:flex;flex-direction:column;align-items:center;">
      <div style="background:#a33900;color:#fff;padding:4px 8px;border-radius:8px;border:2px solid white;font-weight:700;font-size:13px;line-height:1;box-shadow:0 2px 8px rgba(0,0,0,0.4);">
        <span class="material-symbols-outlined" style="font-size:16px;vertical-align:middle;">local_gas_station</span>
      </div>
      <div style="width:0;height:0;border-left:7px solid transparent;border-right:7px solid transparent;border-top:8px solid #a33900;margin-top:-1px;"></div>
    </div>
  `,
  className: '',
  iconSize: [40, 36],
  iconAnchor: [20, 36],
});

/** Force tiles to repaint when the container size settles. */
function MapInvalidator() {
  const map = useMap();
  useEffect(() => {
    const t = setTimeout(() => map.invalidateSize(), 50);
    return () => clearTimeout(t);
  }, [map]);
  return null;
}

export function StationDetailScreen({ stationId }: Props) {
  const f = useFilters();
  const { t } = useI18n();
  const nav = useViewNav();
  const fav = useFavorites();
  const [station, setStation] = useState<Station | null>(null);
  const [toast, setToast] = useState<string | null>(null);

  // Auto-dismiss the toast after 2.5 s.
  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(null), 2500);
    return () => clearTimeout(t);
  }, [toast]);
  const [history, setHistory] = useState<Record<string, [number, number][]>>({});
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    (async () => {
      // Try to find the station by scanning departments around the user —
      // the bbox index covers France plus the Spanish/Portuguese datasets,
      // so foreign stations resolve through the exact same path.
      const tryDepts = new Set<string>();
      if (f.userLocation) {
        const around = await deptsAround(
          f.userLocation.lat,
          f.userLocation.lng,
          Math.max(f.radiusKm, 15),
        );
        for (const d of around ?? []) tryDepts.add(d);
      }
      // Fallback when the index is unavailable or the location is unset:
      // Île-de-France dept codes (small JSONs, served from cache after first hit).
      if (tryDepts.size === 0) {
        for (const d of ['75', '92', '93', '94', '77', '78', '91', '95']) tryDepts.add(d);
      }

      let found: Station | null = null;
      for (const dept of tryDepts) {
        const list = await fetchDepartment(dept);
        const match = list.find((s) => s.id === stationId);
        if (match) {
          found = match;
          // load department history while we have the dept
          const hist = await fetchDeptHistory(dept);
          if (!cancelled) setHistory(hist[String(stationId)] ?? {});
          break;
        }
      }
      if (!cancelled) {
        setStation(found);
        setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [stationId, f.userLocation, f.radiusKm]);

  if (loading) {
    return (
      <div className="absolute inset-0 flex items-center justify-center text-on-surface-variant">
        {t('common.loading')}
      </div>
    );
  }

  if (!station) {
    return (
      <div className="absolute inset-0 flex flex-col items-center justify-center gap-4 text-on-surface-variant">
        <p>{t('station.notFound')}</p>
        <button onClick={() => nav.goBack()} className="text-primary underline">
          {t('common.back')}
        </button>
      </div>
    );
  }

  const distance = f.userLocation
    ? haversineKm(f.userLocation.lat, f.userLocation.lng, station.lat, station.lng)
    : null;

  const trendDays = 7;
  const sevenDayPoints = (fuel: FuelType) => {
    const arr = history[fuel] ?? [];
    if (arr.length === 0) {
      const main = station.fuels[fuel];
      return main ? [{ date: main.d, price: main.p }] : [];
    }
    return arr.slice(-trendDays).map(([epoch, price]) => ({
      date: new Date(epoch).toISOString(),
      price,
    }));
  };

  const trendDelta = (fuel: FuelType): number | null => {
    const arr = history[fuel] ?? [];
    if (arr.length < 2) return null;
    const last = arr[arr.length - 1][1];
    const target = arr[Math.max(0, arr.length - trendDays - 1)][1];
    return Number((last - target).toFixed(3));
  };

  const directionsHref = `https://www.google.com/maps/dir/?api=1&destination=${station.lat},${station.lng}`;

  const share = async () => {
    const url = window.location.href;
    const title = `${station.brand ?? t('station.fallbackNameId', { id: station.id })} — FuelRadar`;
    const text = `${station.brand ?? t('station.fallbackNameId', { id: station.id })} • ${station.cp} ${station.city}`;
    if (navigator.share) {
      try {
        await navigator.share({ title, text, url });
        return;
      } catch {
        // user cancelled or share failed → fall through to clipboard
      }
    }
    try {
      await navigator.clipboard.writeText(url);
      setToast(t('common.linkCopied'));
    } catch {
      window.prompt(t('common.copyLink'), url);
    }
  };

  return (
    <div className="absolute inset-0 overflow-y-auto overscroll-contain pb-8">
      {/* Map header */}
      <div className="relative w-full h-48 md:h-64">
        <MapContainer
          center={[station.lat, station.lng]}
          zoom={16}
          dragging={false}
          scrollWheelZoom={false}
          doubleClickZoom={false}
          zoomControl={false}
          touchZoom={false}
          attributionControl={false}
          className="absolute inset-0 z-0"
        >
          <TileLayer url="https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png" />
          <MapInvalidator />
          <Marker position={[station.lat, station.lng]} icon={detailPin} />
        </MapContainer>
        <div className="absolute inset-0 bg-gradient-to-b from-on-surface/40 via-transparent to-on-surface/30 pointer-events-none" />
        <button
          onClick={() => nav.goBack()}
          className="absolute top-4 left-4 bg-surface-container-lowest text-on-surface p-2 rounded-full shadow-md active:scale-95 transition-transform z-[400]"
          aria-label={t('common.back')}
        >
          <Icon name="arrow_back" />
        </button>
        <div className="absolute top-4 right-4 z-[400] flex gap-2">
          <button
            onClick={share}
            className="bg-surface-container-lowest border border-outline-variant p-2 rounded-full shadow-md active:scale-95 transition-transform"
            aria-label={t('station.shareStation')}
            title={t('common.share')}
          >
            <Icon name="share" className="text-on-surface" />
          </button>
          <button
            onClick={() => fav.toggle(station.id)}
            className="bg-surface-container-lowest border border-outline-variant p-2 rounded-full shadow-md active:scale-95 transition-transform"
            aria-label={fav.isFavorite(station.id) ? t('station.removeFav') : t('station.addFav')}
          >
            <Icon
              name="star"
              filled={fav.isFavorite(station.id)}
              className={fav.isFavorite(station.id) ? 'text-primary' : 'text-on-surface'}
            />
          </button>
        </div>
      </div>

      <main className="w-full max-w-3xl mx-auto px-md pb-lg pt-md space-y-lg relative z-10">
        {/* Identity card */}
        <section className="bg-surface-container-lowest p-lg rounded-xl shadow-[0_4px_12px_rgba(20,27,43,0.1)]">
          <div className="flex flex-col md:flex-row md:items-end md:justify-between gap-md">
            <div className="min-w-0">
              <h1 className="text-headline-lg font-semibold text-on-surface truncate">
                {station.brand ?? t('station.fallbackNameId', { id: station.id })}
              </h1>
              <p className="text-body-sm text-on-surface-variant mt-1 flex items-start gap-1">
                <Icon name="location_on" size={16} />
                <span>
                  {station.addr ? `${station.addr}, ` : ''}
                  {station.cp} {station.city}
                  {distance !== null && ` • ${formatDistance(distance)}`}
                </span>
              </p>
            </div>
            <a
              href={directionsHref}
              target="_blank"
              rel="noopener noreferrer"
              className="bg-primary text-on-primary px-4 py-2 rounded-xl text-body-sm font-semibold flex items-center justify-center gap-2 active:scale-95 transition-transform"
            >
              <Icon name="directions" size={20} /> {t('common.directions')}
            </a>
          </div>
        </section>

        <div className="grid md:grid-cols-2 gap-gutter items-start">
          {/* Prices */}
          <section className="bg-surface-container-lowest p-lg rounded-xl shadow-[0_4px_6px_-1px_rgba(20,27,43,0.1)]">
            <h2 className="text-headline-md font-semibold text-on-surface mb-md flex items-center gap-2 border-b border-surface-variant pb-2">
              <Icon name="local_gas_station" className="text-primary" />
              {t('station.currentPrices')}
            </h2>
            <div className="space-y-sm">
              {(() => {
                const fuelsAvail = FUEL_TYPES.filter((fl) => station.fuels[fl]);
                const ownPrices = fuelsAvail.map((fl) => station.fuels[fl]!.p);
                const bounds = getPriceBounds(ownPrices);
                return fuelsAvail.map((fuel) => {
                const fp = station.fuels[fuel]!;
                const delta = trendDelta(fuel);
                const isMain = fuel === f.selectedFuel;
                const priceColor = getPriceColor(fp.p, bounds.pMin, bounds.pMax);
                return (
                  <div
                    key={fuel}
                    className={[
                      'flex justify-between items-center p-3 rounded-lg border transition-colors',
                      isMain
                        ? 'bg-surface-container-low border-secondary'
                        : 'bg-surface-container border-surface-variant hover:border-primary',
                    ].join(' ')}
                  >
                    <div className="flex items-center gap-sm">
                      <span
                        className={[
                          'text-label-caps font-bold tracking-wider px-3 py-1 rounded-full',
                          isMain
                            ? 'bg-secondary text-on-secondary'
                            : 'bg-surface-variant text-on-surface',
                        ].join(' ')}
                      >
                        {fuel}
                      </span>
                    </div>
                    <div className="text-right">
                      <div
                        className="text-headline-lg font-bold whitespace-nowrap"
                        style={{ color: priceColor }}
                      >
                        {formatPrice(fp.p)} €
                      </div>
                      {delta !== null && delta !== 0 ? (
                        <div
                          className={[
                            'text-[11px] flex items-center justify-end gap-1',
                            delta < 0 ? 'text-tertiary' : 'text-on-surface-variant',
                          ].join(' ')}
                        >
                          <Icon
                            name={delta < 0 ? 'trending_down' : 'trending_up'}
                            size={12}
                          />
                          {t('station.perWeek', { delta: formatPriceDelta(delta) })}
                        </div>
                      ) : (
                        <div className="text-body-sm text-on-surface-variant">
                          {timeAgo(fp.d)}
                        </div>
                      )}
                    </div>
                  </div>
                );
                });
              })()}
            </div>
          </section>

          {/* Trend chart */}
          <section className="bg-surface-container-lowest p-lg rounded-xl shadow-[0_4px_6px_-1px_rgba(20,27,43,0.1)]">
            <h2 className="text-headline-md font-semibold text-on-surface mb-md flex items-center gap-2 border-b border-surface-variant pb-2">
              <Icon name="insights" className="text-secondary" />
              {t('station.trend7d', { fuel: f.selectedFuel })}
            </h2>
            <PriceTrendBars points={sevenDayPoints(f.selectedFuel)} />
          </section>
        </div>

        {(station.h24 || (station.services && station.services.length > 0)) && (
          <section className="bg-surface-container-lowest p-lg rounded-xl shadow-[0_4px_6px_-1px_rgba(20,27,43,0.1)]">
            <h2 className="text-headline-md font-semibold text-on-surface mb-md flex items-center gap-2 border-b border-surface-variant pb-2">
              <Icon name="handyman" className="text-secondary" />
              {t('station.services')}
            </h2>
            <div className="grid grid-cols-2 md:grid-cols-3 gap-sm">
              {station.h24 && (
                <div className="flex items-center gap-sm text-body-sm text-on-surface bg-tertiary-container/40 border border-tertiary-fixed-dim/40 px-3 py-2 rounded-lg">
                  <Icon name="schedule" filled className="text-tertiary" />
                  <span>{t('station.h24')}</span>
                </div>
              )}
              {station.services?.map((svc) => (
                <div
                  key={svc}
                  className="flex items-center gap-sm text-body-sm text-on-surface bg-surface-container border border-surface-variant px-3 py-2 rounded-lg"
                >
                  <Icon name={getServiceIcon(svc)} className="text-on-surface-variant" />
                  <span className="truncate" title={svc}>{svc}</span>
                </div>
              ))}
            </div>
          </section>
        )}
      </main>

      {toast && (
        <div
          role="status"
          aria-live="polite"
          className="fixed bottom-24 md:bottom-6 left-1/2 -translate-x-1/2 z-[1200] bg-inverse-surface text-inverse-on-surface px-4 py-2.5 rounded-full shadow-[0_8px_24px_rgba(20,27,43,0.25)] flex items-center gap-2 text-body-sm font-medium animate-[slideUp_220ms_ease-out]"
        >
          <Icon name="check_circle" filled size={18} />
          {toast}
        </div>
      )}
    </div>
  );
}
