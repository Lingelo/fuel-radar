import { useEffect, useState } from 'react';
import { useSettings } from '../state/SettingsContext';
import { useFilters } from '../state/FiltersContext';
import { fetchMeta, timeAgo } from '../lib/data';
import { getBrowserLocation, reverseGeocode } from '../lib/geocode';
import { useForegroundRefresh } from '../hooks/useForegroundRefresh';
import { useGeolocationPermission } from '../hooks/useGeolocationPermission';
import { useI18n, LOCALES } from '../i18n';
import { Icon } from '../components/Icon';

export function SettingsScreen() {
  const s = useSettings();
  const { t, locale, setLocale } = useI18n();
  const f = useFilters();
  const foregroundVersion = useForegroundRefresh();
  const permission = useGeolocationPermission();
  const [lastUpdate, setLastUpdate] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);

  // Re-fetch meta every mount + every time the app returns to foreground.
  // useForegroundRefresh() also calls invalidateStations() which resets
  // the in-memory metaPromise, so this fetch goes back to the network.
  useEffect(() => {
    let cancelled = false;
    fetchMeta().then((m) => {
      if (!cancelled && m) setLastUpdate(m.lastUpdate);
    });
    return () => {
      cancelled = true;
    };
  }, [foregroundVersion]);

  const refreshLocation = async () => {
    setRefreshing(true);
    try {
      const { coords } = await getBrowserLocation();
      if (coords) {
        f.setUserLocation(coords);
        const addr = await reverseGeocode(coords);
        if (addr) f.setSearchLabel([addr.postcode, addr.city].filter(Boolean).join(' '));
        else f.setSearchLabel(`${coords.lat.toFixed(3)}, ${coords.lng.toFixed(3)}`);
      }
    } finally {
      setRefreshing(false);
    }
  };

  const positionLabel =
    f.searchLabel ??
    (f.userLocation
      ? `${f.userLocation.lat.toFixed(3)}, ${f.userLocation.lng.toFixed(3)}`
      : t('settings.positionUnknown'));

  const APP_URL = 'https://lingelo.github.io/carburants-france/';
  const [shareToast, setShareToast] = useState<string | null>(null);
  useEffect(() => {
    if (!shareToast) return;
    const t = setTimeout(() => setShareToast(null), 2500);
    return () => clearTimeout(t);
  }, [shareToast]);
  const shareApp = async () => {
    const payload = {
      title: 'FuelRadar',
      text: t('settings.shareText'),
      url: APP_URL,
    };
    if (navigator.share) {
      try {
        await navigator.share(payload);
        return;
      } catch {
        // user cancelled → silent
      }
    }
    try {
      await navigator.clipboard.writeText(APP_URL);
      setShareToast(t('common.linkCopied'));
    } catch {
      window.prompt(t('common.copyLink'), APP_URL);
    }
  };

  return (
    <div className="h-full overflow-y-auto">
      <main className="max-w-2xl mx-auto px-md py-lg space-y-lg">
        <h1 className="text-headline-lg font-semibold text-on-surface">{t('settings.title')}</h1>

        <section className="bg-surface-container-lowest rounded-xl p-md shadow-[0_4px_6px_-1px_rgba(0,0,0,0.05)] border border-surface-container-highest">
          <h2 className="text-headline-md font-semibold text-on-surface mb-sm">{t('settings.startScreen')}</h2>
          <div className="grid grid-cols-2 gap-2">
            {(
              [
                { v: 'map', label: t('nav.map') },
                { v: 'stations', label: t('settings.startList') },
              ] as const
            ).map(({ v, label }) => (
              <button
                key={v}
                onClick={() => s.setDefaultStart(v)}
                className={[
                  'p-3 rounded-lg border text-body-sm transition-colors',
                  s.defaultStart === v
                    ? 'bg-secondary text-on-secondary border-secondary'
                    : 'bg-surface-container border-outline text-on-surface',
                ].join(' ')}
              >
                {label}
              </button>
            ))}
          </div>
        </section>

        <section className="bg-surface-container-lowest rounded-xl p-md shadow-[0_4px_6px_-1px_rgba(0,0,0,0.05)] border border-surface-container-highest">
          <h2 className="text-headline-md font-semibold text-on-surface mb-sm flex items-center gap-2">
            <Icon name="language" size={20} className="text-primary" />
            {t('settings.language')}
          </h2>
          <div className="grid grid-cols-2 gap-2">
            {LOCALES.map(({ code, label }) => (
              <button
                key={code}
                onClick={() => setLocale(code)}
                className={[
                  'p-3 rounded-lg border text-body-sm transition-colors',
                  locale === code
                    ? 'bg-secondary text-on-secondary border-secondary'
                    : 'bg-surface-container border-outline text-on-surface',
                ].join(' ')}
              >
                {label}
              </button>
            ))}
          </div>
        </section>

        <section className="bg-surface-container-lowest rounded-xl p-md shadow-[0_4px_6px_-1px_rgba(0,0,0,0.05)] border border-surface-container-highest">
          <h2 className="text-headline-md font-semibold text-on-surface mb-sm">{t('settings.search')}</h2>
          <label className="flex items-start justify-between gap-2 p-2 cursor-pointer">
            <div className="flex-1">
              <div className="text-body-lg text-on-surface">{t('settings.staleWarn')}</div>
              <div className="text-body-sm text-on-surface-variant">
                {t('settings.staleWarnDesc')}
              </div>
            </div>
            <input
              type="checkbox"
              checked={s.showStaleWarning}
              onChange={(e) => s.setShowStaleWarning(e.target.checked)}
              className="w-5 h-5 accent-secondary mt-1"
            />
          </label>
          <div className="p-2 space-y-2">
            <div className="flex items-center justify-between gap-2">
              <div className="min-w-0 flex-1">
                <div className="text-body-lg text-on-surface">{t('settings.currentPosition')}</div>
                <div className="text-body-sm text-on-surface-variant truncate">
                  {positionLabel}
                </div>
              </div>
              <button
                onClick={refreshLocation}
                disabled={
                  refreshing || permission === 'denied' || permission === 'unsupported'
                }
                className="bg-secondary-container text-on-secondary-container px-3 py-2 rounded-lg text-label-caps font-bold tracking-wider flex items-center gap-1 active:scale-95 transition-transform disabled:opacity-60 disabled:cursor-not-allowed"
              >
                <Icon name={refreshing ? 'sync' : 'my_location'} size={16} />
                {refreshing ? t('stations.locating') : t('settings.refresh')}
              </button>
            </div>
            {permission !== 'unknown' && (
              <div
                className={[
                  'flex items-center gap-1.5 text-body-sm',
                  permission === 'granted'
                    ? 'text-primary'
                    : permission === 'denied'
                      ? 'text-error'
                      : 'text-on-surface-variant',
                ].join(' ')}
              >
                <Icon
                  name={
                    permission === 'granted'
                      ? 'check_circle'
                      : permission === 'denied'
                        ? 'location_disabled'
                        : permission === 'unsupported'
                          ? 'block'
                          : 'help'
                  }
                  size={16}
                  filled={permission === 'granted'}
                />
                <span>
                  {permission === 'granted' && t('settings.geoGranted')}
                  {permission === 'prompt' && t('settings.geoPrompt')}
                  {permission === 'denied' && t('settings.geoDenied')}
                  {permission === 'unsupported' && t('settings.geoUnsupported')}
                </span>
              </div>
            )}
            {permission === 'denied' && (
              <details className="bg-error-container text-on-error-container rounded-lg text-body-sm border border-error/30 group">
                <summary className="cursor-pointer list-none flex items-center justify-between gap-2 px-3 py-2 font-medium">
                  <span className="flex items-center gap-1.5">
                    <Icon name="info" size={16} />
                    {t('settings.howReenable')}
                  </span>
                  <Icon
                    name="expand_more"
                    size={18}
                    className="transition-transform group-open:rotate-180"
                  />
                </summary>
                <ol className="list-decimal pl-8 pr-3 pb-3 space-y-1">
                  <li>{t('settings.step1')}</li>
                  <li>{t('settings.step2')}</li>
                  <li>{t('settings.step3')}</li>
                </ol>
              </details>
            )}
          </div>
        </section>

        <section className="bg-surface-container-lowest rounded-xl p-md shadow-[0_4px_6px_-1px_rgba(0,0,0,0.05)] border border-surface-container-highest">
          <h2 className="text-headline-md font-semibold text-on-surface mb-sm flex items-center gap-2">
            <Icon name="share" className="text-primary" />
            {t('settings.shareApp')}
          </h2>
          <p className="text-body-sm text-on-surface-variant mb-3">
            {t('settings.shareAppDesc')}
          </p>
          <button
            onClick={shareApp}
            className="bg-primary text-on-primary px-4 py-2 rounded-lg text-body-sm font-semibold flex items-center gap-2 active:scale-95 transition-transform"
          >
            <Icon name={'share' in navigator ? 'share' : 'content_copy'} size={18} />
            {'share' in navigator ? t('common.share') : t('common.copyLinkBtn')}
          </button>
        </section>

        <section className="bg-surface-container-lowest rounded-xl p-md shadow-[0_4px_6px_-1px_rgba(0,0,0,0.05)] border border-surface-container-highest">
          <h2 className="text-headline-md font-semibold text-on-surface mb-sm">{t('settings.data')}</h2>
          <div className="flex items-center justify-between p-2">
            <span className="text-body-lg text-on-surface">{t('settings.lastUpdate')}</span>
            <span className="text-body-sm text-on-surface-variant">
              {lastUpdate ? timeAgo(lastUpdate) : '—'}
            </span>
          </div>
          <p className="text-body-sm text-on-surface-variant px-2 pt-1">
            {t('settings.sources')}
          </p>
        </section>

        <section className="bg-surface-container-lowest rounded-xl p-md shadow-[0_4px_6px_-1px_rgba(0,0,0,0.05)] border border-surface-container-highest space-y-2">
          <h2 className="text-headline-md font-semibold text-on-surface mb-sm">{t('settings.about')}</h2>
          <p className="text-body-sm text-on-surface-variant flex items-start gap-2">
            <Icon name="info" size={18} />
            <span>{t('settings.aboutText')}</span>
          </p>
          <div className="flex items-start gap-2 text-body-sm text-on-surface-variant pt-2 border-t border-surface-variant">
            <Icon name="code" size={18} />
            <span>
              {t('settings.designedBy')}{' '}
              <a
                href="https://angelo-lima.fr"
                target="_blank"
                rel="noopener noreferrer"
                className="text-primary font-semibold hover:underline"
              >
                Angelo Lima
              </a>
              .
            </span>
          </div>
          <a
            href="https://github.com/Lingelo/carburants-france"
            target="_blank"
            rel="noopener noreferrer"
            className="flex items-center gap-2 text-body-sm text-on-surface-variant hover:text-primary"
          >
            <Icon name="open_in_new" size={16} />
            {t('settings.sourceCode')}
          </a>
        </section>
      </main>

      {shareToast && (
        <div
          role="status"
          aria-live="polite"
          className="fixed bottom-24 md:bottom-6 left-1/2 -translate-x-1/2 z-[1200] bg-inverse-surface text-inverse-on-surface px-4 py-2.5 rounded-full shadow-[0_8px_24px_rgba(22,29,27,0.25)] flex items-center gap-2 text-body-sm font-medium animate-[slideUp_220ms_ease-out]"
        >
          <Icon name="check_circle" filled size={18} />
          {shareToast}
        </div>
      )}
    </div>
  );
}
