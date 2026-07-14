import { useEffect, useState } from 'react';
import { registerSW } from 'virtual:pwa-register';
import { useI18n } from '../i18n';
import { Icon } from './Icon';

/**
 * In `autoUpdate` mode, vite-plugin-pwa swaps the service worker as
 * soon as the new build is ready. We still listen to onNeedRefresh so we
 * can show a brief, non-blocking toast inviting the user to reload —
 * the swap itself is automatic, the reload is offered to apply the new
 * code immediately rather than at the next manual reopen.
 */
export function UpdateBanner() {
  const { t } = useI18n();
  const [needRefresh, setNeedRefresh] = useState(false);
  const [updateSW, setUpdateSW] = useState<((reload?: boolean) => Promise<void>) | null>(null);

  useEffect(() => {
    const update = registerSW({
      immediate: true,
      onNeedRefresh() {
        setNeedRefresh(true);
      },
      onRegisterError(error) {
        console.error('SW registration error:', error);
      },
    });
    setUpdateSW(() => update);
  }, []);

  if (!needRefresh) return null;

  return (
    <div className="fixed bottom-20 md:bottom-6 left-1/2 -translate-x-1/2 z-[1100] bg-surface-container-lowest border border-outline-variant rounded-full shadow-[0_4px_24px_rgba(20,27,43,0.18)] px-4 py-2 flex items-center gap-2 animate-[slideUp_280ms_ease-out]">
      <Icon name="system_update" className="text-primary" />
      <span className="text-body-sm text-on-surface">{t('update.newVersion')}</span>
      <button
        onClick={() => updateSW?.(true)}
        className="bg-primary text-on-primary px-3 py-1.5 rounded-full text-label-caps font-bold tracking-wider flex items-center gap-1 active:scale-95 transition-transform"
        aria-label={t('update.reloadAria')}
      >
        <Icon name="refresh" size={16} />
        {t('update.reload')}
      </button>
      <button
        onClick={() => setNeedRefresh(false)}
        className="p-1 rounded-full text-on-surface-variant hover:bg-surface-container active:scale-95"
        aria-label={t('common.later')}
      >
        <Icon name="close" size={18} />
      </button>
    </div>
  );
}
