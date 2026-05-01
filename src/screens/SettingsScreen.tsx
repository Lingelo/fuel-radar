import { useEffect, useState } from 'react';
import { useSettings } from '../state/SettingsContext';
import { useFilters } from '../state/FiltersContext';
import { fetchMeta, timeAgo } from '../lib/data';
import { getBrowserLocation, reverseGeocode } from '../lib/geocode';
import { Icon } from '../components/Icon';

export function SettingsScreen() {
  const s = useSettings();
  const f = useFilters();
  const [lastUpdate, setLastUpdate] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);

  useEffect(() => {
    fetchMeta().then((m) => m && setLastUpdate(m.lastUpdate));
  }, []);

  const refreshLocation = async () => {
    setRefreshing(true);
    try {
      const c = await getBrowserLocation();
      if (c) {
        f.setUserLocation(c);
        const addr = await reverseGeocode(c);
        if (addr) f.setSearchLabel(`${addr.postcode} ${addr.city}`);
        else f.setSearchLabel(`${c.lat.toFixed(3)}, ${c.lng.toFixed(3)}`);
      }
    } finally {
      setRefreshing(false);
    }
  };

  const positionLabel =
    f.searchLabel ??
    (f.userLocation
      ? `${f.userLocation.lat.toFixed(3)}, ${f.userLocation.lng.toFixed(3)}`
      : 'Indéterminée');

  const APP_URL = 'https://lingelo.github.io/carburants-france/';
  const [shareToast, setShareToast] = useState<string | null>(null);
  useEffect(() => {
    if (!shareToast) return;
    const t = setTimeout(() => setShareToast(null), 2500);
    return () => clearTimeout(t);
  }, [shareToast]);
  const shareApp = async () => {
    const payload = {
      title: 'Carburants France',
      text: 'Compare les prix carburants en France en temps réel — données prix-carburants.gouv.fr.',
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
      setShareToast('Lien copié dans le presse-papier');
    } catch {
      window.prompt('Copier ce lien :', APP_URL);
    }
  };

  return (
    <div className="h-full overflow-y-auto">
      <main className="max-w-2xl mx-auto px-md py-lg space-y-lg">
        <h1 className="text-headline-lg font-semibold text-on-surface">Réglages</h1>

        <section className="bg-surface-container-lowest rounded-xl p-md shadow-[0_4px_6px_-1px_rgba(0,0,0,0.05)] border border-surface-container-highest">
          <h2 className="text-headline-md font-semibold text-on-surface mb-sm">Écran de démarrage</h2>
          <div className="grid grid-cols-2 gap-2">
            {(
              [
                { v: 'map', label: 'Carte' },
                { v: 'stations', label: 'Liste' },
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
          <h2 className="text-headline-md font-semibold text-on-surface mb-sm">Recherche</h2>
          <label className="flex items-start justify-between gap-2 p-2 cursor-pointer">
            <div className="flex-1">
              <div className="text-body-lg text-on-surface">Avertir si données &gt; 72 h</div>
              <div className="text-body-sm text-on-surface-variant">
                Affiche une icône d'alerte rouge à côté du prix d'une station si la dernière mise à jour gouvernementale a plus de 72 h.
              </div>
            </div>
            <input
              type="checkbox"
              checked={s.showStaleWarning}
              onChange={(e) => s.setShowStaleWarning(e.target.checked)}
              className="w-5 h-5 accent-secondary mt-1"
            />
          </label>
          <div className="flex items-center justify-between gap-2 p-2">
            <div className="min-w-0 flex-1">
              <div className="text-body-lg text-on-surface">Position actuelle</div>
              <div className="text-body-sm text-on-surface-variant truncate">
                {positionLabel}
              </div>
            </div>
            <button
              onClick={refreshLocation}
              disabled={refreshing}
              className="bg-secondary-container text-on-secondary-container px-3 py-2 rounded-lg text-label-caps font-bold tracking-wider flex items-center gap-1 active:scale-95 transition-transform disabled:opacity-60"
            >
              <Icon name={refreshing ? 'sync' : 'my_location'} size={16} />
              {refreshing ? 'Localisation…' : 'Actualiser'}
            </button>
          </div>
        </section>

        <section className="bg-surface-container-lowest rounded-xl p-md shadow-[0_4px_6px_-1px_rgba(0,0,0,0.05)] border border-surface-container-highest">
          <h2 className="text-headline-md font-semibold text-on-surface mb-sm flex items-center gap-2">
            <Icon name="share" className="text-primary" />
            Partager l'application
          </h2>
          <p className="text-body-sm text-on-surface-variant mb-3">
            Aide tes proches à comparer les prix carburants : envoie-leur le lien direct vers l'app.
          </p>
          <button
            onClick={shareApp}
            className="bg-primary text-on-primary px-4 py-2 rounded-lg text-body-sm font-semibold flex items-center gap-2 active:scale-95 transition-transform"
          >
            <Icon name={'share' in navigator ? 'share' : 'content_copy'} size={18} />
            {'share' in navigator ? 'Partager' : 'Copier le lien'}
          </button>
        </section>

        <section className="bg-surface-container-lowest rounded-xl p-md shadow-[0_4px_6px_-1px_rgba(0,0,0,0.05)] border border-surface-container-highest">
          <h2 className="text-headline-md font-semibold text-on-surface mb-sm">Données</h2>
          <div className="flex items-center justify-between p-2">
            <span className="text-body-lg text-on-surface">Dernière mise à jour</span>
            <span className="text-body-sm text-on-surface-variant">
              {lastUpdate ? timeAgo(lastUpdate) : '—'}
            </span>
          </div>
          <p className="text-body-sm text-on-surface-variant px-2 pt-1">
            Source : prix-carburants.gouv.fr (rafraîchissement toutes les 2 h).
          </p>
        </section>

        <section className="bg-surface-container-lowest rounded-xl p-md shadow-[0_4px_6px_-1px_rgba(0,0,0,0.05)] border border-surface-container-highest space-y-2">
          <h2 className="text-headline-md font-semibold text-on-surface mb-sm">À propos</h2>
          <p className="text-body-sm text-on-surface-variant flex items-start gap-2">
            <Icon name="info" size={18} />
            <span>Carburants France — données ouvertes du gouvernement français + OpenStreetMap.</span>
          </p>
          <div className="flex items-start gap-2 text-body-sm text-on-surface-variant pt-2 border-t border-surface-variant">
            <Icon name="code" size={18} />
            <span>
              Conçu et développé par{' '}
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
            Code source sur GitHub
          </a>
        </section>
      </main>

      {shareToast && (
        <div
          role="status"
          aria-live="polite"
          className="fixed bottom-24 md:bottom-6 left-1/2 -translate-x-1/2 z-[1200] bg-inverse-surface text-inverse-on-surface px-4 py-2.5 rounded-full shadow-[0_8px_24px_rgba(20,27,43,0.25)] flex items-center gap-2 text-body-sm font-medium animate-[slideUp_220ms_ease-out]"
        >
          <Icon name="check_circle" filled size={18} />
          {shareToast}
        </div>
      )}
    </div>
  );
}
