import type { FuelType } from '../types';
import { ALL_FUELS, FUEL_COLORS, FUEL_LABELS } from '../utils/fuel';

interface Props {
  selected: FuelType;
  onChange: (fuel: FuelType) => void;
}

// Note: behavior is single-select (radio-like). The plan's R15 mentioned
// "multi-select" but switching to multi would change FUEL_COLORS semantics
// and the App.tsx state shape (FuelType → Set<FuelType>) — that is beyond a
// restyle. Kept single-select; applied role="radiogroup" + aria-checked which
// is the semantically correct ARIA pattern for this behavior.
export function FuelFilter({ selected, onChange }: Props) {
  return (
    <div className="flex gap-1 overflow-x-auto" role="radiogroup" aria-label="Filtrer par carburant">
      {ALL_FUELS.map((fuel) => {
        const isActive = fuel === selected;
        return (
          <button
            key={fuel}
            type="button"
            role="radio"
            aria-checked={isActive}
            onClick={() => onChange(fuel)}
            className={`whitespace-nowrap rounded-full px-3 py-1.5 text-xs font-medium transition-all ${
              isActive
                ? 'text-white shadow-sm'
                : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
            }`}
            style={isActive ? { backgroundColor: FUEL_COLORS[fuel] } : undefined}
          >
            {FUEL_LABELS[fuel]}
          </button>
        );
      })}
    </div>
  );
}
