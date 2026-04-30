import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group';
import type { FuelType } from '../types';
import { ALL_FUELS, FUEL_COLORS, FUEL_LABELS } from '../utils/fuel';

interface Props {
  selected: FuelType;
  onChange: (fuel: FuelType) => void;
}

export function FuelFilter({ selected, onChange }: Props) {
  return (
    <ToggleGroup
      type="single"
      value={selected}
      onValueChange={(v) => v && onChange(v as FuelType)}
      aria-label="Filtrer par carburant"
      className="flex gap-1 overflow-x-auto"
    >
      {ALL_FUELS.map((fuel) => (
        <ToggleGroupItem
          key={fuel}
          value={fuel}
          className="whitespace-nowrap rounded-full border-0 px-3 py-1.5 text-xs font-medium transition-all data-[state=off]:bg-gray-100 data-[state=off]:text-gray-600 data-[state=off]:hover:bg-gray-200 data-[state=on]:text-white data-[state=on]:shadow-sm"
          style={fuel === selected ? { backgroundColor: FUEL_COLORS[fuel] } : undefined}
        >
          {FUEL_LABELS[fuel]}
        </ToggleGroupItem>
      ))}
    </ToggleGroup>
  );
}
