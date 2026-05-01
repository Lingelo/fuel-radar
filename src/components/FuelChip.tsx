import type { FuelType } from '../types';

interface FuelChipProps {
  fuel: FuelType;
  active: boolean;
  onClick?: () => void;
  size?: 'sm' | 'md';
}

export function FuelChip({ fuel, active, onClick, size = 'md' }: FuelChipProps) {
  const padding = size === 'sm' ? 'px-3 py-1' : 'px-4 py-2';
  const base = `shrink-0 ${padding} rounded-full text-label-caps font-bold tracking-wider transition-colors active:scale-95`;
  const tone = active
    ? 'bg-secondary text-on-secondary shadow-sm'
    : 'bg-surface-container-high text-on-surface hover:bg-surface-container-highest';
  return (
    <button onClick={onClick} className={`${base} ${tone}`}>
      {fuel}
    </button>
  );
}
