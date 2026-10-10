import { useSyncExternalStore } from 'react';
import { invalidateHistory, invalidateStations } from '../lib/data';

/** Hidden for at least this long → refresh when the app comes back. */
const STALE_AFTER_HIDDEN_MS = 60_000;
/** Data is regenerated every 2 h; refresh an app left open on screen this often. */
const MAX_AGE_MS = 30 * 60_000;
const CHECK_INTERVAL_MS = 60_000;

/*
 * One app-wide refresh clock, shared by every screen. An installed PWA is
 * rarely reloaded: the OS just suspends and resumes it, sometimes for days,
 * so in-memory data has to be marked stale explicitly. Every consumer gets
 * the same `version` and puts it in its effect deps to re-fetch.
 */
let version = 0;
let lastRefresh = Date.now();
let hiddenSince: number | null = null;
let started = false;
const listeners = new Set<() => void>();

function refresh() {
  lastRefresh = Date.now();
  invalidateStations();
  invalidateHistory();
  version += 1;
  listeners.forEach((l) => l());
}

function start() {
  if (started || typeof document === 'undefined') return;
  started = true;
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') {
      hiddenSince = Date.now();
      return;
    }
    const hiddenFor = hiddenSince === null ? 0 : Date.now() - hiddenSince;
    hiddenSince = null;
    if (hiddenFor >= STALE_AFTER_HIDDEN_MS || Date.now() - lastRefresh >= MAX_AGE_MS) refresh();
  });
  // Back online after a dead zone: whatever loaded meanwhile came from cache.
  window.addEventListener('online', refresh);
  // App kept on screen (e.g. phone mount): no visibility change ever fires.
  setInterval(() => {
    if (document.visibilityState === 'visible' && Date.now() - lastRefresh >= MAX_AGE_MS) {
      refresh();
    }
  }, CHECK_INTERVAL_MS);
}

function subscribe(listener: () => void) {
  start();
  listeners.add(listener);
  return () => listeners.delete(listener);
}

const getVersion = () => version;

/**
 * Integer bumped whenever station/history data should be re-fetched:
 * app resumed after a while, network back, or data simply getting old.
 */
export function useForegroundRefresh(): number {
  return useSyncExternalStore(subscribe, getVersion, getVersion);
}
