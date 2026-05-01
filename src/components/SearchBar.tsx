import { useEffect, useRef, useState } from 'react';
import { searchAddress, type AddressResult } from '../lib/geocode';
import { Icon } from './Icon';

interface Props {
  initialLabel?: string | null;
  onResult: (r: AddressResult) => void;
  onOpenFilters?: () => void;
}

export function SearchBar({ initialLabel, onResult, onOpenFilters }: Props) {
  const [query, setQuery] = useState(initialLabel ?? '');
  const [results, setResults] = useState<AddressResult[]>([]);
  const [open, setOpen] = useState(false);
  const debounceRef = useRef<number | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  // Sync local query when the external label changes (e.g. geoloc resolved).
  useEffect(() => {
    if (initialLabel != null) setQuery(initialLabel);
  }, [initialLabel]);

  useEffect(() => {
    if (debounceRef.current) window.clearTimeout(debounceRef.current);
    if (query.trim().length < 2) {
      setResults([]);
      return;
    }
    debounceRef.current = window.setTimeout(() => {
      searchAddress(query).then(setResults);
    }, 220);
    return () => {
      if (debounceRef.current) window.clearTimeout(debounceRef.current);
    };
  }, [query]);

  return (
    <div className="relative">
      <div className="bg-surface-container-lowest rounded-xl shadow-[0_4px_12px_rgba(20,27,43,0.1)] p-1 flex items-center border border-outline-variant focus-within:border-primary transition-colors">
        <Icon name="search" className="text-on-surface-variant pl-3" />
        <input
          ref={inputRef}
          value={query}
          onChange={(e) => {
            setQuery(e.target.value);
            setOpen(true);
          }}
          onFocus={(e) => {
            setOpen(true);
            // Select-all on focus so a second search doesn't append to the previous label.
            requestAnimationFrame(() => e.target.select());
          }}
          onBlur={() => setTimeout(() => setOpen(false), 150)}
          placeholder="Ville ou code postal"
          className="w-full bg-transparent border-none focus:outline-none text-body-lg text-on-surface py-2 px-3 placeholder:text-on-surface-variant"
        />
        {query && (
          <button
            type="button"
            onClick={() => {
              setQuery('');
              setResults([]);
              inputRef.current?.focus();
            }}
            className="p-1 mr-1 rounded-full text-on-surface-variant hover:bg-surface-container active:scale-95 transition-transform"
            aria-label="Effacer la recherche"
          >
            <Icon name="close" size={18} />
          </button>
        )}
        {onOpenFilters && (
          <button
            onClick={onOpenFilters}
            className="bg-secondary-container text-on-secondary-container px-3 py-1.5 rounded-lg text-label-caps font-bold tracking-wider active:scale-95 transition-transform flex items-center gap-1 mr-1"
          >
            <Icon name="tune" size={16} /> Filtres
          </button>
        )}
      </div>

      {open && results.length > 0 && (
        <ul className="absolute left-0 right-0 top-full mt-1 bg-surface-container-lowest rounded-xl shadow-[0_8px_24px_rgba(20,27,43,0.15)] border border-outline-variant overflow-hidden z-50 max-h-80 overflow-y-auto">
          {results.map((r) => (
            <li
              key={r.label + r.lat + r.lng}
              className="px-4 py-3 hover:bg-surface-container cursor-pointer text-body-sm text-on-surface border-b border-surface-variant last:border-0"
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => {
                onResult(r);
                setQuery(`${r.postcode} ${r.city}`);
                setOpen(false);
                inputRef.current?.blur();
              }}
            >
              <div className="font-medium">{r.city}</div>
              <div className="text-on-surface-variant text-[12px]">{r.label}</div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
