import { useEffect, useState, useMemo, useCallback, useRef } from 'react';
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog';
import { VisuallyHidden } from 'radix-ui';
import type { FuelType } from '../types';
import { FUEL_COLORS, FUEL_LABELS } from '../utils/fuel';

interface HistoryData {
  fuels: Record<string, [number, number][]>; // fuel -> [epoch, price][]
  updated: string;
}

type Period = '1M' | '3M' | '6M' | '1A' | 'Max';

const PERIODS: { key: Period; label: string; days: number }[] = [
  { key: '1M', label: '1M', days: 30 },
  { key: '3M', label: '3M', days: 90 },
  { key: '6M', label: '6M', days: 180 },
  { key: '1A', label: '1A', days: 365 },
  { key: 'Max', label: 'Max', days: Infinity },
];

const CHART_FUELS: FuelType[] = ['Gazole', 'E10', 'SP95', 'SP98', 'E85'];

const BASE_URL = import.meta.env.BASE_URL;

const DAY_MS = 86_400_000;

/**
 * Downsample a series to at most `maxPoints` by averaging buckets.
 * Keeps the original data if it's already small enough.
 */
function downsample(
  series: [number, number][],
  maxPoints: number,
): [number, number][] {
  if (series.length <= maxPoints) return series;

  const bucketSize = Math.ceil(series.length / maxPoints);
  const result: [number, number][] = [];

  for (let i = 0; i < series.length; i += bucketSize) {
    const bucket = series.slice(i, i + bucketSize);
    const avgEpoch = bucket.reduce((s, b) => s + b[0], 0) / bucket.length;
    const avgPrice = bucket.reduce((s, b) => s + b[1], 0) / bucket.length;
    result.push([
      Math.round(avgEpoch),
      Math.round(avgPrice * 1000) / 1000,
    ]);
  }

  return result;
}

/**
 * Filter a series to only keep points within the last N days.
 */
function filterByPeriod(
  series: [number, number][],
  days: number,
): [number, number][] {
  if (days === Infinity) return series;
  const cutoff = Date.now() - days * DAY_MS;
  return series.filter(([e]) => e >= cutoff);
}

/**
 * Choose max display points based on period to keep the chart readable.
 */
function maxPointsForPeriod(days: number): number {
  if (days <= 90) return 90; // daily granularity
  if (days <= 365) return 52; // ~weekly
  return 104; // ~biweekly for multi-year
}

export function PriceHistoryModal({ onClose }: { onClose: () => void }) {
  const [data, setData] = useState<HistoryData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [period, setPeriod] = useState<Period>('Max');
  const [enabledFuels, setEnabledFuels] = useState<Set<FuelType>>(
    () => new Set(['Gazole', 'E10', 'SP98']),
  );
  const [tooltip, setTooltip] = useState<{
    x: number;
    y: number;
    date: string;
    prices: { fuel: FuelType; price: number }[];
  } | null>(null);
  const svgRef = useRef<SVGSVGElement>(null);

  useEffect(() => {
    fetch(`${BASE_URL}data/history.json`)
      .then((r) => {
        if (!r.ok) throw new Error('Données historiques non disponibles');
        return r.json();
      })
      .then(setData)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);

  const toggleFuel = useCallback((fuel: FuelType) => {
    setEnabledFuels((prev) => {
      const next = new Set(prev);
      if (next.has(fuel)) {
        if (next.size > 1) next.delete(fuel);
      } else {
        next.add(fuel);
      }
      return next;
    });
  }, []);

  // Chart dimensions
  const margin = { top: 20, right: 16, bottom: 32, left: 48 };
  const width = 600;
  const height = 300;
  const innerW = width - margin.left - margin.right;
  const innerH = height - margin.top - margin.bottom;

  const selectedPeriod = PERIODS.find((p) => p.key === period)!;

  const chartData = useMemo(() => {
    if (!data) return null;

    const activeFuels = CHART_FUELS.filter((f) => enabledFuels.has(f));
    if (activeFuels.length === 0) return null;

    const maxPts = maxPointsForPeriod(selectedPeriod.days);

    // Filter + downsample per fuel
    const processed: Record<string, [number, number][]> = {};
    for (const fuel of activeFuels) {
      const raw = data.fuels[fuel];
      if (!raw?.length) continue;
      const filtered = filterByPeriod(raw, selectedPeriod.days);
      processed[fuel] = downsample(filtered, maxPts);
    }

    // Find global min/max
    let minEpoch = Infinity,
      maxEpoch = -Infinity;
    let minPrice = Infinity,
      maxPrice = -Infinity;

    for (const fuel of activeFuels) {
      const series = processed[fuel];
      if (!series?.length) continue;
      for (const [e, p] of series) {
        if (e < minEpoch) minEpoch = e;
        if (e > maxEpoch) maxEpoch = e;
        if (p < minPrice) minPrice = p;
        if (p > maxPrice) maxPrice = p;
      }
    }

    if (minEpoch === Infinity) return null;

    // Add padding to price range
    const pricePad = (maxPrice - minPrice) * 0.1 || 0.05;
    minPrice -= pricePad;
    maxPrice += pricePad;

    const scaleX = (epoch: number) =>
      ((epoch - minEpoch) / (maxEpoch - minEpoch || 1)) * innerW;
    const scaleY = (price: number) =>
      innerH - ((price - minPrice) / (maxPrice - minPrice || 1)) * innerH;

    // Build SVG paths
    const paths: {
      fuel: FuelType;
      d: string;
      points: [number, number, number, number][];
    }[] = [];
    for (const fuel of activeFuels) {
      const series = processed[fuel];
      if (!series?.length) continue;
      const pts: [number, number, number, number][] = series.map(([e, p]) => [
        scaleX(e),
        scaleY(p),
        e,
        p,
      ]);
      const d = pts
        .map(([x, y], i) => `${i === 0 ? 'M' : 'L'}${x},${y}`)
        .join('');
      paths.push({ fuel: fuel as FuelType, d, points: pts });
    }

    // Y-axis ticks
    const yTickCount = 5;
    const yTicks: { value: number; y: number }[] = [];
    for (let i = 0; i <= yTickCount; i++) {
      const value = minPrice + ((maxPrice - minPrice) * i) / yTickCount;
      yTicks.push({ value, y: scaleY(value) });
    }

    // X-axis ticks — adapt to period
    const xTicks: { label: string; x: number }[] = [];
    const spanDays = (maxEpoch - minEpoch) / DAY_MS;
    const months = [
      'Jan',
      'Fév',
      'Mar',
      'Avr',
      'Mai',
      'Jun',
      'Jul',
      'Aoû',
      'Sep',
      'Oct',
      'Nov',
      'Déc',
    ];

    if (spanDays <= 45) {
      // Weekly ticks for short periods
      const start = new Date(minEpoch);
      const dayOfWeek = start.getUTCDay();
      const firstMonday = new Date(
        minEpoch + ((8 - dayOfWeek) % 7) * DAY_MS,
      );
      const cursor = new Date(firstMonday);
      while (cursor.getTime() <= maxEpoch) {
        const epoch = cursor.getTime();
        xTicks.push({
          label: `${cursor.getUTCDate()} ${months[cursor.getUTCMonth()]}`,
          x: scaleX(epoch),
        });
        cursor.setUTCDate(cursor.getUTCDate() + 7);
      }
    } else {
      // Monthly ticks
      const startDate = new Date(minEpoch);
      const endDate = new Date(maxEpoch);
      const current = new Date(
        startDate.getFullYear(),
        startDate.getMonth(),
        1,
      );
      while (current <= endDate) {
        const epoch = current.getTime();
        if (epoch >= minEpoch && epoch <= maxEpoch) {
          const label =
            spanDays > 400
              ? `${months[current.getMonth()]} ${String(current.getFullYear()).slice(2)}`
              : months[current.getMonth()];
          xTicks.push({ label, x: scaleX(epoch) });
        }
        current.setMonth(current.getMonth() + 1);
      }
    }

    return {
      paths,
      yTicks,
      xTicks,
      minEpoch,
      maxEpoch,
      minPrice,
      maxPrice,
      scaleX,
      scaleY,
    };
  }, [data, enabledFuels, selectedPeriod, innerW, innerH]);

  // Binary search for closest point (replaces linear scan)
  const findClosest = useCallback(
    (points: [number, number, number, number][], targetEpoch: number) => {
      let lo = 0;
      let hi = points.length - 1;
      while (lo < hi) {
        const mid = (lo + hi) >> 1;
        if (points[mid][2] < targetEpoch) lo = mid + 1;
        else hi = mid;
      }
      // Check lo and lo-1
      if (lo > 0) {
        const distLo = Math.abs(points[lo][2] - targetEpoch);
        const distPrev = Math.abs(points[lo - 1][2] - targetEpoch);
        if (distPrev < distLo) return points[lo - 1];
      }
      return points[lo];
    },
    [],
  );

  const handleMouseMove = useCallback(
    (e: React.MouseEvent<SVGSVGElement>) => {
      if (!chartData || !svgRef.current) return;
      const rect = svgRef.current.getBoundingClientRect();
      const svgX =
        ((e.clientX - rect.left) / rect.width) * width - margin.left;

      if (svgX < 0 || svgX > innerW) {
        setTooltip(null);
        return;
      }

      const targetEpoch =
        chartData.minEpoch +
        (svgX / innerW) * (chartData.maxEpoch - chartData.minEpoch);
      const maxDist =
        (chartData.maxEpoch - chartData.minEpoch) * 0.02;

      const prices: { fuel: FuelType; price: number }[] = [];
      let closestDate = '';

      for (const { fuel, points } of chartData.paths) {
        const best = findClosest(points, targetEpoch);
        const dist = Math.abs(best[2] - targetEpoch);
        if (dist < maxDist) {
          prices.push({ fuel, price: best[3] });
          if (!closestDate) {
            closestDate = new Date(best[2]).toLocaleDateString('fr-FR', {
              day: 'numeric',
              month: 'short',
              year: 'numeric',
            });
          }
        }
      }

      if (prices.length > 0) {
        setTooltip({
          x: svgX + margin.left,
          y: margin.top,
          date: closestDate,
          prices: prices.sort((a, b) => b.price - a.price),
        });
      } else {
        setTooltip(null);
      }
    },
    [chartData, findClosest, innerW, margin, width],
  );

  // Touch support for mobile
  const handleTouchMove = useCallback(
    (e: React.TouchEvent<SVGSVGElement>) => {
      const touch = e.touches[0];
      handleMouseMove({
        clientX: touch.clientX,
        clientY: touch.clientY,
        currentTarget: e.currentTarget,
      } as unknown as React.MouseEvent<SVGSVGElement>);
    },
    [handleMouseMove],
  );

  return (
    <Dialog open onOpenChange={(open) => { if (!open) onClose(); }}>
      <DialogContent className="max-w-5xl">
        <VisuallyHidden.Root>
          <DialogTitle>Évolution des prix</DialogTitle>
        </VisuallyHidden.Root>
        <h2 className="mb-4 text-lg font-bold text-gray-800">Évolution des prix</h2>

        {/* Period selector */}
        <div className="mb-3 flex items-center gap-1">
          {PERIODS.map(({ key, label }) => (
            <button
              key={key}
              onClick={() => setPeriod(key)}
              className={`rounded-lg px-3 py-1 text-xs font-medium transition-all ${
                period === key
                  ? 'bg-gray-800 text-white'
                  : 'text-gray-500 hover:bg-gray-100'
              }`}
            >
              {label}
            </button>
          ))}
        </div>

        {/* Fuel toggles */}
        <div className="mb-4 flex flex-wrap gap-2">
          {CHART_FUELS.map((fuel) => (
            <button
              key={fuel}
              onClick={() => toggleFuel(fuel)}
              className="flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-medium transition-all"
              style={{
                backgroundColor: enabledFuels.has(fuel)
                  ? FUEL_COLORS[fuel] + '20'
                  : '#f3f4f6',
                color: enabledFuels.has(fuel) ? FUEL_COLORS[fuel] : '#9ca3af',
                borderWidth: 1,
                borderColor: enabledFuels.has(fuel)
                  ? FUEL_COLORS[fuel] + '40'
                  : 'transparent',
              }}
            >
              <span
                className="h-2 w-2 rounded-full"
                style={{
                  backgroundColor: enabledFuels.has(fuel)
                    ? FUEL_COLORS[fuel]
                    : '#d1d5db',
                }}
              />
              {FUEL_LABELS[fuel]}
            </button>
          ))}
        </div>

        {/* Chart */}
        <div className="relative">
          {loading && (
            <div className="flex h-[300px] items-center justify-center text-sm text-gray-400">
              Chargement...
            </div>
          )}
          {error && (
            <div className="flex h-[300px] items-center justify-center text-sm text-red-500">
              {error}
            </div>
          )}
          {chartData && (
            <svg
              ref={svgRef}
              viewBox={`0 0 ${width} ${height}`}
              className="w-full touch-none"
              onMouseMove={handleMouseMove}
              onMouseLeave={() => setTooltip(null)}
              onTouchMove={handleTouchMove}
              onTouchEnd={() => setTooltip(null)}
            >
              <g transform={`translate(${margin.left},${margin.top})`}>
                {/* Grid lines */}
                {chartData.yTicks.map((t, i) => (
                  <g key={i}>
                    <line
                      x1={0}
                      x2={innerW}
                      y1={t.y}
                      y2={t.y}
                      stroke="#e5e7eb"
                      strokeDasharray="4,4"
                    />
                    <text
                      x={-8}
                      y={t.y}
                      textAnchor="end"
                      dominantBaseline="middle"
                      className="fill-gray-400"
                      fontSize={11}
                    >
                      {t.value.toFixed(2)}€
                    </text>
                  </g>
                ))}

                {/* X-axis ticks */}
                {chartData.xTicks.map((t, i) => (
                  <text
                    key={i}
                    x={t.x}
                    y={innerH + 20}
                    textAnchor="middle"
                    className="fill-gray-400"
                    fontSize={11}
                  >
                    {t.label}
                  </text>
                ))}

                {/* Lines */}
                {chartData.paths.map(({ fuel, d }) => (
                  <path
                    key={fuel}
                    d={d}
                    fill="none"
                    stroke={FUEL_COLORS[fuel]}
                    strokeWidth={2}
                    strokeLinejoin="round"
                    strokeLinecap="round"
                  />
                ))}

                {/* Tooltip vertical line */}
                {tooltip && (
                  <line
                    x1={tooltip.x - margin.left}
                    x2={tooltip.x - margin.left}
                    y1={0}
                    y2={innerH}
                    stroke="#9ca3af"
                    strokeWidth={1}
                    strokeDasharray="4,4"
                  />
                )}
              </g>
            </svg>
          )}

          {/* Tooltip overlay */}
          {tooltip && (
            <div
              className="pointer-events-none absolute top-0 z-10 rounded-lg bg-white/95 px-3 py-2 text-xs shadow-lg backdrop-blur-sm"
              style={{
                left: `${(tooltip.x / width) * 100}%`,
                transform:
                  tooltip.x > width * 0.7
                    ? 'translateX(-110%)'
                    : 'translateX(10px)',
              }}
            >
              <div className="mb-1 font-semibold text-gray-600">
                {tooltip.date}
              </div>
              {tooltip.prices.map(({ fuel, price }) => (
                <div key={fuel} className="flex items-center gap-2">
                  <span
                    className="h-1.5 w-1.5 rounded-full"
                    style={{ backgroundColor: FUEL_COLORS[fuel] }}
                  />
                  <span className="text-gray-500">{FUEL_LABELS[fuel]}</span>
                  <span
                    className="ml-auto font-medium"
                    style={{ color: FUEL_COLORS[fuel] }}
                  >
                    {price.toFixed(3).replace('.', ',')}€
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Footer */}
        {data?.updated && (
          <p className="mt-3 text-center text-[10px] text-gray-400">
            Moyennes nationales journalières — Source : prix-carburants.gouv.fr
          </p>
        )}
      </DialogContent>
    </Dialog>
  );
}
