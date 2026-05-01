import { useEffect, useState } from 'react';
import { invalidateStations } from '../lib/data';

/**
 * Bumps an integer whenever the document becomes visible again after at
 * least `staleAfterMs` of being hidden. Components can include this
 * value in their useEffect deps to force a re-fetch with fresh data
 * when the user comes back to the app.
 */
export function useForegroundRefresh(staleAfterMs = 60_000): number {
  const [version, setVersion] = useState(0);

  useEffect(() => {
    let hiddenSince: number | null = null;
    const onVisibility = () => {
      if (document.visibilityState === 'hidden') {
        hiddenSince = Date.now();
        return;
      }
      if (hiddenSince === null) return;
      const elapsed = Date.now() - hiddenSince;
      hiddenSince = null;
      if (elapsed < staleAfterMs) return;
      // Foreground after being away long enough — wipe caches and bump.
      invalidateStations().finally(() => setVersion((v) => v + 1));
    };
    document.addEventListener('visibilitychange', onVisibility);
    return () => document.removeEventListener('visibilitychange', onVisibility);
  }, [staleAfterMs]);

  return version;
}
