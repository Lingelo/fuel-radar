import { useMemo, useRef, useState } from 'react';
import type { FuelType } from '../types';
import { formatPrice } from '../lib/format';

interface Series {
  fuel: FuelType;
  points: [number, number][];
}

interface Props {
  series: Series[];
  fuelColors: Record<FuelType, string>;
  /** Render variant — controls padding and font sizing inside the SVG. */
  size?: 'inline' | 'fullscreen';
}

/**
 * Multi-line price chart with a vertical cursor that follows the user's
 * pointer (mouse or finger). Reports the closest data point per series.
 */
export function PriceChart({ series, fuelColors, size = 'inline' }: Props) {
  const ref = useRef<SVGSVGElement | null>(null);
  const [cursorT, setCursorT] = useState<number | null>(null);

  const W = 1000;
  const H = size === 'fullscreen' ? 480 : 320;
  const PAD = size === 'fullscreen'
    ? { top: 28, right: 28, bottom: 36, left: 60 }
    : { top: 20, right: 20, bottom: 28, left: 50 };

  const { allMin, allMax, tStart, tEnd } = useMemo(() => {
    let allMin = Infinity;
    let allMax = -Infinity;
    let tStart = Infinity;
    let tEnd = -Infinity;
    for (const s of series) {
      for (const [t, p] of s.points) {
        if (p < allMin) allMin = p;
        if (p > allMax) allMax = p;
        if (t < tStart) tStart = t;
        if (t > tEnd) tEnd = t;
      }
    }
    if (!isFinite(allMin)) return { allMin: 0, allMax: 0, tStart: 0, tEnd: 0 };
    return { allMin, allMax, tStart, tEnd };
  }, [series]);

  const x = (t: number) =>
    PAD.left + ((t - tStart) / Math.max(1, tEnd - tStart)) * (W - PAD.left - PAD.right);
  const y = (p: number) =>
    PAD.top + (1 - (p - allMin) / Math.max(0.01, allMax - allMin)) * (H - PAD.top - PAD.bottom);

  const yTicks = useMemo(() => {
    if (!isFinite(allMin) || allMin === allMax) return [];
    const range = allMax - allMin;
    const step = range / 4;
    return [0, 1, 2, 3, 4].map((i) => allMin + i * step);
  }, [allMin, allMax]);

  const onMove = (clientX: number) => {
    const svg = ref.current;
    if (!svg) return;
    const rect = svg.getBoundingClientRect();
    const ratio = (clientX - rect.left) / rect.width;
    const px = ratio * W;
    if (px < PAD.left || px > W - PAD.right) {
      setCursorT(null);
      return;
    }
    const t = tStart + ((px - PAD.left) / (W - PAD.left - PAD.right)) * (tEnd - tStart);
    setCursorT(t);
  };

  // For each series, snap to the closest available data point relative to cursorT.
  const snapped = useMemo(() => {
    if (cursorT === null) return null;
    const out: { fuel: FuelType; t: number; p: number }[] = [];
    for (const s of series) {
      let best: [number, number] | null = null;
      let bestDelta = Infinity;
      for (const [t, p] of s.points) {
        const d = Math.abs(t - cursorT);
        if (d < bestDelta) {
          bestDelta = d;
          best = [t, p];
        }
      }
      if (best) out.push({ fuel: s.fuel, t: best[0], p: best[1] });
    }
    if (out.length === 0) return null;
    // All series share roughly the same x — use the first one for the
    // cursor anchor (they're sampled daily so divergence is minimal).
    return { ts: out[0].t, points: out };
  }, [cursorT, series]);

  if (series.length === 0) {
    return (
      <p className="text-body-sm text-on-surface-variant py-lg text-center">
        Aucune donnée. Sélectionne un carburant.
      </p>
    );
  }

  return (
    <div className="relative">
      <svg
        ref={ref}
        viewBox={`0 0 ${W} ${H}`}
        className="w-full h-auto select-none touch-none"
        onMouseMove={(e) => onMove(e.clientX)}
        onMouseLeave={() => setCursorT(null)}
        onTouchStart={(e) => e.touches[0] && onMove(e.touches[0].clientX)}
        onTouchMove={(e) => e.touches[0] && onMove(e.touches[0].clientX)}
        onTouchEnd={() => setCursorT(null)}
      >
        {/* Y grid */}
        {yTicks.map((p, i) => (
          <g key={i}>
            <line
              x1={PAD.left}
              x2={W - PAD.right}
              y1={y(p)}
              y2={y(p)}
              stroke="var(--color-surface-variant)"
              strokeWidth={1}
            />
            <text
              x={PAD.left - 8}
              y={y(p) + 4}
              textAnchor="end"
              fontSize={size === 'fullscreen' ? 13 : 11}
              fill="var(--color-on-surface-variant)"
            >
              {formatPrice(p, 2)} €
            </text>
          </g>
        ))}

        {/* X axis labels (start, mid, end) */}
        <text
          x={PAD.left}
          y={H - 8}
          fontSize={size === 'fullscreen' ? 13 : 11}
          fill="var(--color-on-surface-variant)"
        >
          {new Date(tStart).toLocaleDateString('fr-FR', { day: '2-digit', month: 'short' })}
        </text>
        <text
          x={(PAD.left + (W - PAD.right)) / 2}
          y={H - 8}
          textAnchor="middle"
          fontSize={size === 'fullscreen' ? 13 : 11}
          fill="var(--color-on-surface-variant)"
        >
          {new Date((tStart + tEnd) / 2).toLocaleDateString('fr-FR', { day: '2-digit', month: 'short' })}
        </text>
        <text
          x={W - PAD.right}
          y={H - 8}
          textAnchor="end"
          fontSize={size === 'fullscreen' ? 13 : 11}
          fill="var(--color-on-surface-variant)"
        >
          {new Date(tEnd).toLocaleDateString('fr-FR', { day: '2-digit', month: 'short' })}
        </text>

        {/* Lines */}
        {series.map((s) => {
          const path = s.points
            .map((pt, i) => `${i === 0 ? 'M' : 'L'}${x(pt[0]).toFixed(1)} ${y(pt[1]).toFixed(1)}`)
            .join(' ');
          return (
            <path
              key={s.fuel}
              d={path}
              fill="none"
              stroke={fuelColors[s.fuel]}
              strokeWidth={size === 'fullscreen' ? 3 : 2.5}
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          );
        })}

        {/* Cursor */}
        {snapped && (
          <g>
            <line
              x1={x(snapped.ts)}
              x2={x(snapped.ts)}
              y1={PAD.top}
              y2={H - PAD.bottom}
              stroke="var(--color-on-surface)"
              strokeWidth={1}
              strokeDasharray="4 3"
              opacity={0.4}
            />
            {snapped.points.map((pt) => (
              <circle
                key={pt.fuel}
                cx={x(pt.t)}
                cy={y(pt.p)}
                r={size === 'fullscreen' ? 6 : 4.5}
                fill={fuelColors[pt.fuel]}
                stroke="white"
                strokeWidth={2}
              />
            ))}
          </g>
        )}
      </svg>

      {/* Tooltip overlay */}
      {snapped && (
        <div
          className="absolute top-2 right-2 bg-inverse-surface text-inverse-on-surface rounded-lg px-3 py-2 shadow-lg pointer-events-none"
          style={{ fontSize: size === 'fullscreen' ? 14 : 12 }}
        >
          <div className="font-semibold mb-1">
            {new Date(snapped.ts).toLocaleDateString('fr-FR', {
              day: 'numeric',
              month: 'short',
              year: 'numeric',
            })}
          </div>
          <div className="space-y-0.5">
            {snapped.points.map((pt) => (
              <div key={pt.fuel} className="flex items-center gap-2 whitespace-nowrap">
                <span
                  className="w-2.5 h-2.5 rounded-full"
                  style={{ background: fuelColors[pt.fuel] }}
                />
                <span className="font-bold tracking-wider uppercase opacity-80">{pt.fuel}</span>
                <span className="ml-auto font-mono">{formatPrice(pt.p)} €</span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
