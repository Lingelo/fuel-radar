interface Point {
  date: string;
  price: number;
}

interface Props {
  points: Point[];
}

export function PriceTrendBars({ points }: Props) {
  if (points.length === 0) {
    return (
      <div className="h-32 flex items-center justify-center text-body-sm text-on-surface-variant">
        Pas d'historique disponible
      </div>
    );
  }
  const min = Math.min(...points.map((p) => p.price));
  const max = Math.max(...points.map((p) => p.price));
  const range = Math.max(0.01, max - min);

  return (
    <div>
      <div className="h-40 w-full flex items-end justify-between gap-1 pt-8">
        {points.map((p, i) => {
          const heightPct = 30 + ((p.price - min) / range) * 70;
          const isLast = i === points.length - 1;
          return (
            <div
              key={p.date + i}
              className={`w-full rounded-t-sm relative group transition-colors ${
                isLast ? 'bg-secondary' : 'bg-surface-variant hover:bg-secondary'
              }`}
              style={{ height: `${heightPct}%` }}
            >
              <div
                className={`absolute -top-8 left-1/2 -translate-x-1/2 bg-inverse-surface text-inverse-on-surface text-[10px] font-bold tracking-wider px-2 py-1 rounded transition-opacity whitespace-nowrap ${
                  isLast ? 'opacity-100' : 'opacity-0 group-hover:opacity-100'
                }`}
              >
                {p.price.toFixed(3).replace('.', ',')}&nbsp;€
              </div>
            </div>
          );
        })}
      </div>
      <div className="flex justify-between mt-2 text-label-caps font-bold tracking-wider text-on-surface-variant">
        <span>{formatLabel(points[0].date)}</span>
        <span>Aujourd'hui</span>
      </div>
    </div>
  );
}

function formatLabel(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleDateString('fr-FR', { weekday: 'short' });
}
