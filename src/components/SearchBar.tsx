import { useState, useRef, useEffect } from 'react';
import type { CityResult } from '../types';

interface Props {
  query: string;
  onSearch: (q: string) => void;
  results: CityResult[];
  loading: boolean;
  error?: 'network' | null;
  searched?: boolean;
  onRetry?: () => void;
  onSelect: (city: CityResult) => void;
  onClear: () => void;
}

export function SearchBar({ query, onSearch, results, loading, error, searched, onRetry, onSelect, onClear }: Props) {
  const inputRef = useRef<HTMLInputElement>(null);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const [highlightIndex, setHighlightIndex] = useState(-1);

  // Reset highlight when results change. eslint complains about setState in
  // effect, but the alternatives (deriving from results, key prop on listbox)
  // are messier and not worth the structural cost here.
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setHighlightIndex(-1);
  }, [results]);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (
        dropdownRef.current &&
        !dropdownRef.current.contains(e.target as Node) &&
        inputRef.current &&
        !inputRef.current.contains(e.target as Node)
      ) {
        onClear();
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [onClear]);

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (!results.length) {
      if (e.key === 'Enter') {
        // Let debounce finish — no-op, results will appear
      }
      return;
    }

    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setHighlightIndex((prev) => (prev < results.length - 1 ? prev + 1 : 0));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setHighlightIndex((prev) => (prev > 0 ? prev - 1 : results.length - 1));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      const idx = highlightIndex >= 0 ? highlightIndex : 0;
      if (results[idx]) {
        onSelect(results[idx]);
      }
    } else if (e.key === 'Escape') {
      onClear();
      inputRef.current?.blur();
    }
  };

  return (
    <div className="relative w-full">
      <div className="relative">
        <svg
          className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
          />
        </svg>
        <input
          ref={inputRef}
          id="city-search"
          type="text"
          value={query}
          onChange={(e) => onSearch(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Rechercher une ville (rayon 10 km)..."
          className="w-full rounded-xl border-0 bg-white py-2.5 pl-10 pr-10 text-sm text-gray-900 shadow-lg ring-1 ring-gray-200 placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-primary"
          role="combobox"
          aria-expanded={results.length > 0 && query.length >= 2}
          aria-controls="city-search-listbox"
          aria-activedescendant={highlightIndex >= 0 ? `city-option-${highlightIndex}` : undefined}
          aria-autocomplete="list"
        />
        {query && (
          <button
            onClick={() => {
              onSearch('');
              onClear();
              inputRef.current?.focus();
            }}
            className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
          >
            <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        )}
      </div>

      {query.length >= 2 && (loading || results.length > 0 || error || (searched && !loading && results.length === 0)) && (
        <div
          ref={dropdownRef}
          className="absolute top-full z-[1100] mt-1 max-h-60 w-full overflow-y-auto rounded-xl bg-white shadow-xl ring-1 ring-gray-200"
        >
          {error ? (
            <div className="flex items-center justify-between gap-2 px-4 py-3 text-sm" role="alert">
              <span className="text-gray-600">Erreur réseau</span>
              <button
                type="button"
                onClick={() => {
                  onRetry?.();
                  inputRef.current?.focus();
                }}
                className="rounded-md px-2 py-1 text-xs font-semibold text-primary hover:bg-primary/10"
              >
                Réessayer
              </button>
            </div>
          ) : loading && results.length === 0 ? (
            <div className="px-4 py-3 text-sm text-gray-400" role="status" aria-live="polite">Recherche...</div>
          ) : results.length === 0 ? (
            <div className="px-4 py-3 text-sm text-gray-400" role="status" aria-live="polite">Aucune ville trouvée</div>
          ) : (
            <ul id="city-search-listbox" role="listbox" aria-label="Villes trouvées">
              {results.map((city, i) => (
                <li key={`${city.postcode}-${i}`}>
                  <button
                    id={`city-option-${i}`}
                    role="option"
                    aria-selected={i === highlightIndex}
                    onClick={() => onSelect(city)}
                    className={`flex w-full items-center gap-3 px-4 py-2.5 text-left text-sm transition-colors ${
                      i === highlightIndex ? 'bg-primary/10' : 'hover:bg-gray-50'
                    }`}
                  >
                    <svg className="h-4 w-4 shrink-0 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z" />
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
                    </svg>
                    <div>
                      <span className="font-medium text-gray-800">{city.name}</span>
                      <span className="ml-2 text-gray-400">{city.postcode}</span>
                    </div>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
