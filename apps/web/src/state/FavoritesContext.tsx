import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';

const KEY = 'fuelfinder-favorites-v1';

interface State {
  favorites: Set<number>;
  toggle: (id: number) => void;
  isFavorite: (id: number) => boolean;
}

const Ctx = createContext<State | null>(null);

export function FavoritesProvider({ children }: { children: ReactNode }) {
  const [favorites, setFavorites] = useState<Set<number>>(() => {
    try {
      const raw = localStorage.getItem(KEY);
      if (!raw) return new Set();
      return new Set(JSON.parse(raw) as number[]);
    } catch {
      return new Set();
    }
  });

  useEffect(() => {
    try {
      localStorage.setItem(KEY, JSON.stringify([...favorites]));
    } catch {
      // ignore quota errors
    }
  }, [favorites]);

  const toggle = useCallback((id: number) => {
    setFavorites((s) => {
      const next = new Set(s);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }, []);

  const isFavorite = useCallback((id: number) => favorites.has(id), [favorites]);

  const value = useMemo(() => ({ favorites, toggle, isFavorite }), [favorites, toggle, isFavorite]);

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useFavorites(): State {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error('useFavorites must be used inside <FavoritesProvider>');
  return ctx;
}
