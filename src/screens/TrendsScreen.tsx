import { useEffect, useMemo, useState } from 'react';
import { fetchNationalHistory, timeAgo, type NationalHistory } from '../lib/data';
import { FUEL_TYPES, type FuelType } from '../types';
import { Icon } from '../components/Icon';
import { PriceChart } from '../components/PriceChart';
import { formatPrice, formatPercent } from '../lib/format';

const FUEL_COLORS: Record<FuelType, string> = {
  Gazole: '#ea580c',
  E10: '#0d9488',
  SP95: '#16a34a',
  SP98: '#2563eb',
  E85: '#7c3aed',
  GPLc: '#525252',
};

const RANGES = [
  { label: '30 j', days: 30 },
  { label: '90 j', days: 90 },
  { label: '1 an', days: 365 },
] as const;

export function TrendsScreen() {
  const [data, setData] = useState<NationalHistory | null>(null);
  const [loading, setLoading] = useState(true);
  const [days, setDays] = useState<30 | 90 | 365>(90);
  const [activeFuels, setActiveFuels] = useState<Set<FuelType>>(
    new Set<FuelType>(['Gazole', 'E10', 'SP98']),
  );
  const [fullscreen, setFullscreen] = useState(false);

  useEffect(() => {
    fetchNationalHistory()
      .then((d) => setData(d))
      .finally(() => setLoading(false));
  }, []);

  // Lock body scroll while in fullscreen.
  useEffect(() => {
    if (!fullscreen) return;
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = prev;
    };
  }, [fullscreen]);

  // Allow Escape to close fullscreen.
  useEffect(() => {
    if (!fullscreen) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setFullscreen(false);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [fullscreen]);

  const series = useMemo(() => {
    if (!data?.fuels) return [];
    const cutoff = Date.now() - days * 86400_000;
    return FUEL_TYPES.filter((f) => activeFuels.has(f))
      .map((f) => {
        const arr = (data.fuels[f] ?? []).filter(([t]) => t >= cutoff);
        return { fuel: f, points: arr };
      })
      .filter((s) => s.points.length > 0);
  }, [data, days, activeFuels]);

  const latest = (fuel: FuelType): { price: number; date: number } | null => {
    const arr = data?.fuels?.[fuel] ?? [];
    if (arr.length === 0) return null;
    const [t, p] = arr[arr.length - 1];
    return { price: p, date: t };
  };

  const yearChange = (fuel: FuelType): number | null => {
    const arr = data?.fuels?.[fuel] ?? [];
    if (arr.length < 2) return null;
    const cutoff = Date.now() - 365 * 86400_000;
    const past = arr.find(([t]) => t >= cutoff)?.[1] ?? arr[0][1];
    const last = arr[arr.length - 1][1];
    return ((last - past) / past) * 100;
  };

  const toggleFuel = (f: FuelType) => {
    setActiveFuels((s) => {
      const next = new Set(s);
      if (next.has(f)) next.delete(f);
      else next.add(f);
      return next;
    });
  };

  const RangeSwitch = (
    <div className="flex gap-1 bg-surface-container-lowest rounded-lg p-1 border border-surface-variant">
      {RANGES.map((r) => (
        <button
          key={r.days}
          onClick={() => setDays(r.days as 30 | 90 | 365)}
          className={[
            'px-3 py-1 rounded text-body-sm transition-colors',
            days === r.days
              ? 'bg-secondary text-on-secondary font-medium'
              : 'text-on-surface hover:bg-surface-container',
          ].join(' ')}
        >
          {r.label}
        </button>
      ))}
    </div>
  );

  const Legend = (
    <div className="flex flex-wrap gap-3">
      {series.map((s) => (
        <span key={s.fuel} className="flex items-center gap-1 text-body-sm text-on-surface">
          <span
            className="w-3 h-3 rounded-full inline-block"
            style={{ background: FUEL_COLORS[s.fuel] }}
          />
          {s.fuel}
        </span>
      ))}
    </div>
  );

  return (
    <>
      <div className="h-full overflow-y-auto">
        <main className="max-w-5xl mx-auto px-md py-lg space-y-lg">
          <header className="flex items-center justify-between flex-wrap gap-2">
            <div>
              <h1 className="text-headline-lg font-semibold text-on-surface">
                Tendances nationales
              </h1>
              <p className="text-body-sm text-on-surface-variant">
                Moyennes journalières des prix carburants en France.{' '}
                {data?.updated && `Mis à jour ${timeAgo(data.updated)}.`}
              </p>
            </div>
            {RangeSwitch}
          </header>

          {/* KPI cards */}
          <section className="grid grid-cols-2 md:grid-cols-3 gap-gutter">
            {FUEL_TYPES.filter((f) => latest(f) !== null).map((f) => {
              const lat = latest(f)!;
              const yc = yearChange(f);
              return (
                <button
                  key={f}
                  onClick={() => toggleFuel(f)}
                  className={[
                    'bg-surface-container-lowest rounded-xl p-md text-left border transition-colors',
                    activeFuels.has(f) ? 'border-secondary' : 'border-surface-variant opacity-60',
                  ].join(' ')}
                >
                  <div className="flex items-center gap-2 mb-1">
                    <span
                      className="w-3 h-3 rounded-full inline-block"
                      style={{ background: FUEL_COLORS[f] }}
                    />
                    <span className="text-label-caps font-bold tracking-wider text-on-surface">{f}</span>
                  </div>
                  <div className="text-headline-lg font-bold text-on-surface whitespace-nowrap">
                    {formatPrice(lat.price)} €
                  </div>
                  {yc !== null && (
                    <div
                      className={[
                        'text-body-sm flex items-center gap-1 mt-1',
                        yc < 0 ? 'text-tertiary' : 'text-error',
                      ].join(' ')}
                    >
                      <Icon name={yc < 0 ? 'trending_down' : 'trending_up'} size={14} />
                      {formatPercent(yc)} sur 1 an
                    </div>
                  )}
                </button>
              );
            })}
          </section>

          {/* Chart */}
          <section className="bg-surface-container-lowest p-md rounded-xl shadow-[0_4px_6px_-1px_rgba(0,0,0,0.05)] border border-surface-container-highest">
            <div className="flex items-center justify-between mb-sm">
              <h2 className="text-headline-md font-semibold text-on-surface flex items-center gap-2">
                <Icon name="insights" className="text-secondary" />
                Évolution des prix
              </h2>
              <button
                onClick={() => setFullscreen(true)}
                className="text-body-sm text-primary px-2.5 py-1 rounded-lg hover:bg-surface-container active:scale-95 transition-transform flex items-center gap-1"
                aria-label="Afficher en plein écran"
                title="Plein écran"
              >
                <Icon name="fullscreen" size={18} /> Plein écran
              </button>
            </div>
            {loading ? (
              <p className="text-body-sm text-on-surface-variant py-lg text-center">Chargement…</p>
            ) : (
              <PriceChart series={series} fuelColors={FUEL_COLORS} />
            )}
            <div className="mt-sm">{Legend}</div>
            <p className="text-body-sm text-on-surface-variant mt-2">
              Touche le graphique pour lire les valeurs précises à chaque date.
            </p>
          </section>
        </main>
      </div>

      {/* Fullscreen overlay */}
      {fullscreen && (
        <div className="fixed inset-0 z-[1300] bg-background flex flex-col">
          <header className="flex items-center justify-between gap-2 px-4 h-14 border-b border-outline-variant bg-surface-container-lowest shrink-0">
            <div className="flex items-center gap-2 min-w-0">
              <Icon name="insights" className="text-secondary" />
              <h2 className="text-headline-md font-semibold text-on-surface truncate">
                Évolution des prix nationaux
              </h2>
            </div>
            <div className="flex items-center gap-2 shrink-0">
              {RangeSwitch}
              <button
                onClick={() => setFullscreen(false)}
                className="p-2 rounded-full hover:bg-surface-container active:scale-95 transition-transform"
                aria-label="Quitter le plein écran"
                title="Fermer (Esc)"
              >
                <Icon name="close" />
              </button>
            </div>
          </header>
          <div className="flex-1 overflow-y-auto px-4 py-4 flex flex-col gap-4">
            <div className="flex flex-wrap gap-2">
              {FUEL_TYPES.filter((f) => latest(f) !== null).map((f) => (
                <button
                  key={f}
                  onClick={() => toggleFuel(f)}
                  className={[
                    'px-3 py-1.5 rounded-full text-label-caps font-bold tracking-wider transition-colors flex items-center gap-1.5',
                    activeFuels.has(f)
                      ? 'bg-secondary text-on-secondary'
                      : 'bg-surface-container border border-outline-variant text-on-surface-variant',
                  ].join(' ')}
                >
                  <span
                    className="w-2 h-2 rounded-full"
                    style={{ background: FUEL_COLORS[f] }}
                  />
                  {f}
                </button>
              ))}
            </div>
            <div className="bg-surface-container-lowest rounded-xl p-4 border border-surface-variant flex-1 flex flex-col">
              <div className="flex-1 flex items-center">
                <PriceChart series={series} fuelColors={FUEL_COLORS} size="fullscreen" />
              </div>
              <p className="text-body-sm text-on-surface-variant text-center mt-2">
                Glisse le doigt ou la souris sur le graphique pour lire les prix à une date donnée.
              </p>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
