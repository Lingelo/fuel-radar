import { useState } from 'react';
import { useInstallPrompt } from '../hooks/useInstallPrompt';
import { Icon } from './Icon';

/**
 * Compact "Install" button for the TopAppBar. Adapts to the platform:
 * - Chromium → fires the native install dialog directly
 * - In-app webview → opens a modal explaining to reopen in a real browser
 * - Firefox / generic → opens a modal pointing at the browser menu
 *
 * Hides itself once the app is running standalone or in unsupported contexts
 * (including iOS Safari, where the install flow has to be done manually via
 * Partager → Sur l'écran d'accueil).
 */
export function InstallButton() {
  const inst = useInstallPrompt();
  const [hint, setHint] = useState<null | 'webview' | 'generic'>(null);

  if (inst.installed || inst.platform === 'unsupported') return null;

  const onClick = async () => {
    if (inst.platform === 'native-prompt') {
      await inst.install();
      return;
    }
    if (inst.platform === 'in-app-webview') return setHint('webview');
    setHint('generic');
  };

  return (
    <>
      <button
        onClick={onClick}
        className="bg-primary text-on-primary px-3 py-1.5 rounded-full text-label-caps font-bold tracking-wider flex items-center gap-1 active:scale-95 transition-transform shadow-sm"
        aria-label="Installer l'application"
        title="Installer l'application"
      >
        <Icon name="install_mobile" size={16} />
        Installer
      </button>

      {hint && (
        <div className="fixed inset-0 z-[1100] flex items-end md:items-center justify-center p-0 md:p-4">
          <div className="absolute inset-0 bg-on-surface/40" onClick={() => setHint(null)} />
          <div className="relative bg-surface-container-lowest w-full md:w-[480px] md:rounded-xl rounded-t-xl p-6 shadow-[0_-12px_32px_rgba(20,27,43,0.18)]">
            <div className="flex items-start justify-between gap-2 mb-3">
              <h2 className="text-headline-md font-semibold text-on-surface">
                Installer Carburants
              </h2>
              <button
                onClick={() => setHint(null)}
                className="p-1 rounded-full hover:bg-surface-container"
                aria-label="Fermer"
              >
                <Icon name="close" />
              </button>
            </div>

            {hint === 'webview' && (
              <div className="text-body-lg text-on-surface space-y-2">
                <p>
                  Tu utilises un navigateur intégré (Facebook, Instagram, Gmail, LinkedIn…). Il ne permet pas l'installation.
                </p>
                <p>
                  Touche les <strong>⋮</strong> puis <strong>Ouvrir dans le navigateur</strong> (Chrome ou Safari), puis reviens ici pour installer l'application.
                </p>
              </div>
            )}

            {hint === 'generic' && (
              <div className="text-body-lg text-on-surface space-y-2">
                <p>
                  Sur ton navigateur, ouvre le menu (icône <strong>⋮</strong> en haut à droite, ou dans la barre d'adresse).
                </p>
                <p>
                  Cherche <strong>« Installer Carburants »</strong> ou <strong>« Ajouter à l'écran d'accueil »</strong>, et confirme.
                </p>
              </div>
            )}

            <button
              onClick={() => setHint(null)}
              className="w-full mt-5 bg-primary text-on-primary py-3 rounded-xl text-body-lg font-semibold active:scale-[0.98] transition-transform"
            >
              Compris
            </button>
          </div>
        </div>
      )}
    </>
  );
}
