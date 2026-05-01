import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';

const KEY = 'carburants-france-settings-v1';

export type DefaultStartScreen = 'map' | 'stations';

interface Settings {
  defaultStart: DefaultStartScreen;
  showStaleWarning: boolean;
}

interface State extends Settings {
  setDefaultStart: (s: DefaultStartScreen) => void;
  setShowStaleWarning: (b: boolean) => void;
}

const DEFAULTS: Settings = {
  defaultStart: 'map',
  showStaleWarning: true,
};

const Ctx = createContext<State | null>(null);

export function SettingsProvider({ children }: { children: ReactNode }) {
  const [settings, setSettings] = useState<Settings>(() => {
    try {
      const raw = localStorage.getItem(KEY);
      return raw ? { ...DEFAULTS, ...(JSON.parse(raw) as Settings) } : DEFAULTS;
    } catch {
      return DEFAULTS;
    }
  });

  useEffect(() => {
    try {
      localStorage.setItem(KEY, JSON.stringify(settings));
    } catch {
      // ignore
    }
  }, [settings]);

  const value = useMemo<State>(
    () => ({
      ...settings,
      setDefaultStart: (defaultStart) => setSettings((s) => ({ ...s, defaultStart })),
      setShowStaleWarning: (showStaleWarning) => setSettings((s) => ({ ...s, showStaleWarning })),
    }),
    [settings],
  );

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useSettings(): State {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error('useSettings must be used inside <SettingsProvider>');
  return ctx;
}
