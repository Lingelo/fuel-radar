import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group';
import type { FuelType } from '../types';
import { ALL_FUELS, FUEL_COLORS, FUEL_LABELS } from '../utils/fuel';

interface Props {
  selected: FuelType;
  onChange: (fuel: FuelType) => void;
}

// Visual language aligned with the PriceHistoryModal fuel toggles:
// active = tint fill (FUEL_COLOR @ 12.5%) + colored text + colored
// border + colored dot. Inactive = neutral grey. Single-select behavior
// preserved (radio-group semantics from shadcn ToggleGroup type=single).
export function FuelFilter({ selected, onChange }: Props) {
  return (
    <ToggleGroup
      type="single"
      value={selected}
      onValueChange={(v) => v && onChange(v as FuelType)}
      aria-label="Filtrer par carburant"
      className="flex gap-1 overflow-x-auto"
    >
      {ALL_FUELS.map((fuel) => {
        const isActive = fuel === selected;
        const color = FUEL_COLORS[fuel];
        return (
          <ToggleGroupItem
            key={fuel}
            value={fuel}
            className="flex items-center gap-1.5 whitespace-nowrap rounded-full border px-3 py-1 text-xs font-medium transition-all data-[state=off]:border-transparent data-[state=off]:bg-gray-100 data-[state=off]:text-gray-500 data-[state=off]:hover:bg-gray-200"
            style={isActive ? {
              backgroundColor: color + '20',
              color,
              borderColor: color + '40',
            } : undefined}
          >
            <span
              className="h-2 w-2 rounded-full"
              style={{ backgroundColor: isActive ? color : '#d1d5db' }}
            />
            {FUEL_LABELS[fuel]}
          </ToggleGroupItem>
        );
      })}
    </ToggleGroup>
  );
}
