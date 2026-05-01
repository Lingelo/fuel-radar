import type { FuelType, Station } from '../types';
import { FUEL_TYPES } from '../types';
import { formatDistance } from '../lib/distance';
import { isStale, timeAgo } from '../lib/data';
import { formatPriceEuro } from '../lib/format';
import { getPriceBounds, getPriceColor } from '../lib/priceColor';
import { useSettings } from '../state/SettingsContext';
import { Icon } from './Icon';

interface Props {
  station: Station;
  distanceKm: number;
  selectedFuel: FuelType;
  isFavorite: boolean;
  /** Optional reference price bounds so the popup colors stay aligned with map+list pins. */
  referencePrices?: number[];
  onToggleFavorite: () => void;
  onOpenDetails: () => void;
  onClose: () => void;
}

export function StationPopup({
  station,
  distanceKm,
  selectedFuel,
  isFavorite,
  referencePrices,
  onToggleFavorite,
  onOpenDetails,
  onClose,
}: Props) {
  const settings = useSettings();
  const directions = `https://www.google.com/maps/dir/?api=1&destination=${station.lat},${station.lng}`;
  const fuels = FUEL_TYPES.filter((f) => station.fuels[f]);
  // Build per-fuel bounds: if external reference prices were supplied
  // (selected fuel only), use them; otherwise fall back to this station's
  // own range so the price tints still convey "min to max here".
  const selectedBounds = referencePrices && referencePrices.length > 0
    ? getPriceBounds(referencePrices)
    : null;
  const ownPrices = fuels.map((f) => station.fuels[f]!.p);
  const ownBounds = getPriceBounds(ownPrices);

  return (
    <div className="fixed inset-0 z-[1100] flex items-end md:items-center justify-center p-0 md:p-4">
      <div className="absolute inset-0 bg-on-surface/40" onClick={onClose} />
      <div className="relative bg-surface-container-lowest w-full md:w-[480px] md:max-w-[480px] md:rounded-xl rounded-t-xl p-6 shadow-[0_-12px_32px_rgba(20,27,43,0.18)] max-h-[85vh] overflow-y-auto">
        <div className="flex items-start justify-between gap-md mb-md">
          <div className="min-w-0">
            <h2 className="text-headline-lg font-semibold text-on-surface truncate">
              {station.brand ?? `Station ${station.id}`}
            </h2>
            <p className="text-body-sm text-on-surface-variant flex items-start gap-1 mt-1">
              <Icon name="location_on" size={16} />
              <span>
                {station.addr ? `${station.addr}, ` : ''}
                {station.cp} {station.city} • {formatDistance(distanceKm)}
              </span>
            </p>
          </div>
          <div className="flex gap-1 shrink-0">
            <button
              onClick={onToggleFavorite}
              className="p-2 rounded-full hover:bg-surface-container active:scale-95 transition-transform"
              aria-label={isFavorite ? 'Retirer des favoris' : 'Ajouter aux favoris'}
            >
              <Icon
                name="star"
                filled={isFavorite}
                className={isFavorite ? 'text-primary' : 'text-on-surface-variant'}
              />
            </button>
            <button
              onClick={onClose}
              className="p-2 rounded-full hover:bg-surface-container"
              aria-label="Fermer"
            >
              <Icon name="close" />
            </button>
          </div>
        </div>

        <div className="space-y-2 mb-md">
          {fuels.map((fuel) => {
            const fp = station.fuels[fuel]!;
            const isMain = fuel === selectedFuel;
            const stale = settings.showStaleWarning && isStale(fp.d);
            // Color the price using the same green→red scale used by the
            // map pins and the stations list, so the popup matches the
            // "this station is cheap/expensive" cue at a glance.
            const bounds = isMain && selectedBounds ? selectedBounds : ownBounds;
            const priceColor = getPriceColor(fp.p, bounds.pMin, bounds.pMax);
            return (
              <div
                key={fuel}
                className={[
                  'flex justify-between items-center p-3 rounded-lg border',
                  isMain
                    ? 'bg-surface-container-low border-secondary'
                    : 'bg-surface-container border-surface-variant',
                ].join(' ')}
              >
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
                <div className="text-right">
                  <div
                    className="text-headline-md font-bold whitespace-nowrap"
                    style={{ color: priceColor }}
                  >
                    {formatPriceEuro(fp.p)}
                  </div>
                  <div
                    className={`text-[11px] flex items-center justify-end gap-1 ${stale ? 'text-error' : 'text-on-surface-variant'}`}
                    title={stale ? 'Donnée non rafraîchie depuis plus de 72 h' : undefined}
                  >
                    {stale && <Icon name="warning" size={12} />}
                    {timeAgo(fp.d)}
                  </div>
                </div>
              </div>
            );
          })}
        </div>

        <div className="flex gap-2">
          <button
            onClick={onOpenDetails}
            className="flex-1 bg-surface-container text-on-surface py-3 rounded-xl text-body-sm font-semibold flex items-center justify-center gap-1 active:scale-95 transition-transform"
          >
            Détails <Icon name="arrow_forward" size={18} />
          </button>
          <a
            href={directions}
            target="_blank"
            rel="noopener noreferrer"
            className="flex-1 bg-primary text-on-primary py-3 rounded-xl text-body-sm font-semibold flex items-center justify-center gap-1 active:scale-95 transition-transform"
          >
            <Icon name="directions" size={18} /> Itinéraire
          </a>
        </div>
      </div>
    </div>
  );
}
