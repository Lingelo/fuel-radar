/* eslint-disable react-refresh/only-export-components */
import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import { LOCALE_TAGS, MESSAGES, type Locale, type MessageKey } from './messages';

export { LOCALES, LOCALE_TAGS, type Locale, type MessageKey } from './messages';

const STORAGE_KEY = 'app-locale';

type Params = Record<string, string | number>;

function detectLocale(): Locale {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored && stored in MESSAGES) return stored as Locale;
  } catch {
    // storage unavailable (private mode) — fall through to browser language
  }
  const lang = (navigator.language ?? '').slice(0, 2).toLowerCase();
  if (lang === 'fr' || lang === 'es' || lang === 'pt') return lang;
  return 'en';
}

function format(template: string, params?: Params): string {
  // "singular|plural" forms, picked by the `n` parameter.
  let text = template;
  if (template.includes('|')) {
    const [singular, plural] = template.split('|');
    const n = typeof params?.n === 'number' ? params.n : 1;
    text = n > 1 ? plural : singular;
  }
  if (!params) return text;
  return text.replace(/\{(\w+)\}/g, (m, name) =>
    name in params ? String(params[name]) : m,
  );
}

/**
 * Module-level mirror of the active locale so non-React helpers
 * (e.g. timeAgo in lib/data.ts) can translate without threading `t`
 * through every call site. Kept in sync by I18nProvider.
 */
let activeLocale: Locale = detectLocale();

export function getActiveLocale(): Locale {
  return activeLocale;
}

/** BCP 47 tag of the active locale, for toLocaleDateString & friends. */
export function activeLocaleTag(): string {
  return LOCALE_TAGS[activeLocale];
}

/** Translate outside of React components (uses the active locale). */
export function translate(key: MessageKey, params?: Params): string {
  return format(MESSAGES[activeLocale][key] ?? MESSAGES.fr[key] ?? key, params);
}

interface I18nContextValue {
  locale: Locale;
  setLocale: (l: Locale) => void;
  t: (key: MessageKey, params?: Params) => string;
  /** BCP 47 tag for date formatting in components. */
  localeTag: string;
}

const I18nContext = createContext<I18nContextValue | null>(null);

export function I18nProvider({ children }: { children: ReactNode }) {
  const [locale, setLocaleState] = useState<Locale>(() => detectLocale());

  useEffect(() => {
    activeLocale = locale;
    document.documentElement.lang = locale;
    try {
      localStorage.setItem(STORAGE_KEY, locale);
    } catch {
      // ignore
    }
  }, [locale]);

  const t = useCallback(
    (key: MessageKey, params?: Params) =>
      format(MESSAGES[locale][key] ?? MESSAGES.fr[key] ?? key, params),
    [locale],
  );

  return (
    <I18nContext.Provider
      value={{ locale, setLocale: setLocaleState, t, localeTag: LOCALE_TAGS[locale] }}
    >
      {children}
    </I18nContext.Provider>
  );
}

export function useI18n(): I18nContextValue {
  const ctx = useContext(I18nContext);
  if (!ctx) throw new Error('useI18n must be used within I18nProvider');
  return ctx;
}
