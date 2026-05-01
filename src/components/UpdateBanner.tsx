import { useEffect, useState } from 'react';
import { registerSW } from 'virtual:pwa-register';
import { Icon } from './Icon';

/**
 * Listens to vite-plugin-pwa's service-worker hooks and shows a small
 * toast/button when a new build is ready. Tapping the button calls
 * updateSW() which forces the new SW to take over and reloads the page.
 */
export function UpdateBanner() {
  const [needRefresh, setNeedRefresh] = useState(false);
  const [updateSW, setUpdateSW] = useState<((reload?: boolean) => Promise<void>) | null>(null);

  useEffect(() => {
    const update = registerSW({
      onNeedRefresh() {
        setNeedRefresh(true);
      },
      onRegisterError(error) {
        console.error('SW registration error:', error);
      },
    });
    // registerSW returns a function, store it for later use
    setUpdateSW(() => update);
  }, []);

  if (!needRefresh) return null;

  return (
    <div className="fixed bottom-20 md:bottom-6 left-1/2 -translate-x-1/2 z-[1100] bg-surface-container-lowest border border-outline-variant rounded-full shadow-[0_4px_24px_rgba(20,27,43,0.18)] px-4 py-2 flex items-center gap-2 animate-[slideUp_280ms_ease-out]">
      <Icon name="system_update" className="text-primary" />
      <span className="text-body-sm text-on-surface">Nouvelle version disponible</span>
      <button
        onClick={() => updateSW?.(true)}
        className="bg-primary text-on-primary px-3 py-1.5 rounded-full text-label-caps font-bold tracking-wider flex items-center gap-1 active:scale-95 transition-transform"
        aria-label="Recharger l'application avec la nouvelle version"
      >
        <Icon name="refresh" size={16} />
        Recharger
      </button>
      <button
        onClick={() => setNeedRefresh(false)}
        className="p-1 rounded-full text-on-surface-variant hover:bg-surface-container active:scale-95"
        aria-label="Plus tard"
      >
        <Icon name="close" size={18} />
      </button>
    </div>
  );
}
