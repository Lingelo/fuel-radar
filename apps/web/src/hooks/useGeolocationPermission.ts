import { useEffect, useState } from 'react';

export type GeolocationPermissionState =
  | 'granted'
  | 'denied'
  | 'prompt'
  | 'unsupported'
  | 'unknown';

/**
 * Reactive geolocation permission state. Subscribes to PermissionStatus
 * changes so the UI updates if the user re-authorises (or revokes) from
 * the browser's site settings without reloading the page.
 *
 * Returns 'unsupported' when the Permissions API or Geolocation API is
 * absent (e.g. very old browsers, Safari iOS < 16). Returns 'unknown'
 * during the initial async query before the first resolution.
 */
export function useGeolocationPermission(): GeolocationPermissionState {
  const [state, setState] = useState<GeolocationPermissionState>(() => {
    if (typeof navigator === 'undefined') return 'unsupported';
    if (!('geolocation' in navigator)) return 'unsupported';
    if (!('permissions' in navigator)) return 'unsupported';
    return 'unknown';
  });

  useEffect(() => {
    if (state === 'unsupported') return;
    let cancelled = false;
    let status: PermissionStatus | null = null;
    const onChange = () => {
      if (status) setState(status.state as GeolocationPermissionState);
    };
    navigator.permissions
      .query({ name: 'geolocation' as PermissionName })
      .then((s) => {
        if (cancelled) return;
        status = s;
        setState(s.state as GeolocationPermissionState);
        s.addEventListener('change', onChange);
      })
      .catch(() => {
        if (!cancelled) setState('unsupported');
      });
    return () => {
      cancelled = true;
      if (status) status.removeEventListener('change', onChange);
    };
    // Run once. We intentionally don't re-run on `state` changes — the
    // PermissionStatus listener handles every transition after mount.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return state;
}
