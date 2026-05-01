import { useState } from 'react';
import { FUEL_TYPES, type FuelType } from '../types';
import { useFilters } from '../state/FiltersContext';
import { Icon } from './Icon';

const KNOWN_BRANDS = [
  'TotalEnergies',
  'Leclerc',
  'Intermarché',
  'Système U',
  'Carrefour',
  'Auchan',
  'BP',
  'Shell',
  'Esso',
  'Casino',
  'Avia',
];

interface Props {
  open: boolean;
  onClose: () => void;
}

export function FilterSheet({ open, onClose }: Props) {
  const f = useFilters();
  const [localFuel, setLocalFuel] = useState<FuelType>(f.selectedFuel);
  const [localRadius, setLocalRadius] = useState(f.radiusKm);
  const [localSort, setLocalSort] = useState(f.sortBy);
  const [localBrands, setLocalBrands] = useState<Set<string>>(new Set(f.selectedBrands));

  if (!open) return null;

  const apply = () => {
    f.setSelectedFuel(localFuel);
    f.setRadiusKm(localRadius);
    f.setSortBy(localSort);
    f.setSelectedBrands(localBrands);
    onClose();
  };

  const reset = () => {
    setLocalFuel('Gazole');
    setLocalRadius(10);
    setLocalSort('price');
    setLocalBrands(new Set());
  };

  const toggleBrand = (b: string) => {
    setLocalBrands((s) => {
      const next = new Set(s);
      if (next.has(b)) next.delete(b);
      else next.add(b);
      return next;
    });
  };

  const allSelected = localBrands.size === 0 || localBrands.size === KNOWN_BRANDS.length;
  const toggleAll = () => {
    if (localBrands.size === KNOWN_BRANDS.length) setLocalBrands(new Set());
    else setLocalBrands(new Set(KNOWN_BRANDS));
  };

  return (
    <div className="fixed inset-0 z-[1000] flex flex-col bg-background">
      {/* Header */}
      <header className="sticky top-0 bg-surface-container-lowest border-b border-outline-variant flex justify-between items-center px-4 h-16 w-full z-10">
        <div className="flex items-center gap-sm">
          <button
            onClick={onClose}
            className="text-primary hover:bg-surface-container-low transition-colors p-2 rounded-full active:scale-95"
            aria-label="Fermer"
          >
            <Icon name="close" />
          </button>
          <h1 className="text-xl font-bold text-on-surface tracking-tight">Filtres</h1>
        </div>
        <button
          onClick={reset}
          className="text-primary text-body-sm font-semibold hover:bg-surface-container-low transition-colors px-3 py-2 rounded-lg active:scale-95"
        >
          Réinitialiser
        </button>
      </header>

      {/* Body */}
      <main className="flex-grow overflow-y-auto p-md md:max-w-2xl md:mx-auto w-full pb-32">
        <div className="flex flex-col gap-lg">
          {/* Fuel Type */}
          <section className="bg-surface-container-lowest rounded-xl p-md shadow-[0_4px_6px_-1px_rgba(0,0,0,0.05)] border border-surface-container-highest">
            <h2 className="text-headline-md font-semibold text-on-surface mb-sm">
              Type de carburant
            </h2>
            <div className="flex flex-wrap gap-2">
              {FUEL_TYPES.map((fuel) => (
                <button
                  key={fuel}
                  onClick={() => setLocalFuel(fuel)}
                  className={[
                    'px-4 py-2 rounded-full text-label-caps font-bold tracking-wider transition-colors border',
                    localFuel === fuel
                      ? 'bg-secondary text-on-secondary border-secondary'
                      : 'bg-surface-container-low text-on-surface border-outline',
                  ].join(' ')}
                >
                  {fuel}
                </button>
              ))}
            </div>
          </section>

          {/* Sort By */}
          <section className="bg-surface-container-lowest rounded-xl p-md shadow-[0_4px_6px_-1px_rgba(0,0,0,0.05)] border border-surface-container-highest">
            <h2 className="text-headline-md font-semibold text-on-surface mb-sm">Trier par</h2>
            <div className="grid grid-cols-2 gap-2">
              {(
                [
                  { v: 'price', label: 'Prix (le moins cher)' },
                  { v: 'distance', label: 'Distance (le plus proche)' },
                ] as const
              ).map(({ v, label }) => (
                <label
                  key={v}
                  className={[
                    'flex items-center gap-2 p-3 rounded-lg cursor-pointer border',
                    localSort === v
                      ? 'border-secondary bg-secondary-fixed'
                      : 'border-outline bg-surface-container-lowest',
                  ].join(' ')}
                >
                  <input
                    type="radio"
                    name="sort"
                    checked={localSort === v}
                    onChange={() => setLocalSort(v)}
                    className="w-5 h-5 accent-secondary"
                  />
                  <span className="text-body-lg text-on-surface flex-grow">{label}</span>
                </label>
              ))}
            </div>
          </section>

          {/* Search Radius */}
          <section className="bg-surface-container-lowest rounded-xl p-md shadow-[0_4px_6px_-1px_rgba(0,0,0,0.05)] border border-surface-container-highest">
            <div className="flex justify-between items-center mb-sm">
              <h2 className="text-headline-md font-semibold text-on-surface">
                Rayon de recherche
              </h2>
              <span className="text-headline-md font-semibold text-primary">
                {localRadius} km
              </span>
            </div>
            <div className="py-2">
              <input
                type="range"
                min={1}
                max={30}
                value={Math.min(30, localRadius)}
                onChange={(e) => setLocalRadius(Number(e.target.value))}
                className="w-full h-2 bg-surface-variant rounded-lg appearance-none cursor-pointer accent-primary"
              />
              <div className="flex justify-between text-on-surface-variant text-body-sm mt-1">
                <span>1 km</span>
                <span>30 km</span>
              </div>
              <p className="text-body-sm text-on-surface-variant mt-1">
                Limite à 30 km pour garantir le chargement complet de toutes les stations dans la zone.
              </p>
            </div>
          </section>

          {/* Brands */}
          <section className="bg-surface-container-lowest rounded-xl p-md shadow-[0_4px_6px_-1px_rgba(0,0,0,0.05)] border border-surface-container-highest">
            <div className="flex justify-between items-center mb-sm">
              <h2 className="text-headline-md font-semibold text-on-surface">Enseignes</h2>
              <button
                onClick={toggleAll}
                className="text-body-sm text-secondary hover:underline"
              >
                {allSelected ? 'Tout désélectionner' : 'Tout sélectionner'}
              </button>
            </div>
            <p className="text-body-sm text-on-surface-variant mb-2">
              {localBrands.size === 0
                ? 'Aucun filtre — toutes les enseignes affichées.'
                : `${localBrands.size} enseigne(s) sélectionnée(s)`}
            </p>
            <div className="space-y-1">
              {KNOWN_BRANDS.map((b) => (
                <label
                  key={b}
                  className="flex items-center gap-3 p-2 hover:bg-surface-container-low rounded-lg cursor-pointer transition-colors"
                >
                  <input
                    type="checkbox"
                    checked={localBrands.has(b)}
                    onChange={() => toggleBrand(b)}
                    className="rounded border-outline w-5 h-5 accent-secondary"
                  />
                  <span className="text-body-lg text-on-surface">{b}</span>
                </label>
              ))}
            </div>
          </section>
        </div>
      </main>

      {/* Sticky Bottom Action */}
      <div className="fixed bottom-0 left-0 w-full bg-surface-container-lowest border-t border-surface-container-highest p-4 md:max-w-2xl md:left-1/2 md:-translate-x-1/2 z-20 shadow-[0_-4px_6px_-1px_rgba(0,0,0,0.05)]">
        <button
          onClick={apply}
          className="w-full bg-primary text-on-primary py-3 px-lg rounded-xl text-headline-md font-semibold flex justify-center items-center gap-2 active:scale-[0.98] transition-transform"
        >
          Appliquer les filtres
          <Icon name="check" />
        </button>
      </div>
    </div>
  );
}
