import { useEffect, useState } from 'react';
import { useI18n } from '../i18n';
import { donateUrl, registerVisitAndShouldPrompt, snoozeDonatePrompt } from '../lib/donate';
import { Icon } from './Icon';

/**
 * Invitation aux dons, discrète et refermable : n'apparaît qu'à partir de
 * la 5e visite, puis se met en veille 30 jours (« Plus tard ») ou 180 jours
 * (après un clic sur le lien).
 */
export function DonateBanner() {
  const { t } = useI18n();
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    if (!registerVisitAndShouldPrompt()) return;
    // Let the map settle before asking for anything.
    const id = setTimeout(() => setVisible(true), 8000);
    return () => clearTimeout(id);
  }, []);

  if (!visible) return null;

  const close = (donated: boolean) => {
    snoozeDonatePrompt(donated);
    setVisible(false);
  };

  return (
    <div
      role="dialog"
      aria-label={t('donate.title')}
      className="fixed bottom-36 md:bottom-6 left-1/2 -translate-x-1/2 z-[1050] w-[calc(100%-2rem)] max-w-[28rem] bg-surface-container-lowest border border-outline-variant rounded-xl shadow-[0_4px_24px_rgba(0,0,0,0.18)] p-3 flex items-start gap-3 animate-[slideUp_280ms_ease-out]"
    >
      <Icon name="volunteer_activism" className="text-primary mt-0.5" />
      <div className="flex-1 min-w-0">
        <p className="text-body-sm font-semibold text-on-surface">{t('donate.title')}</p>
        <p className="text-body-sm text-on-surface-variant">{t('donate.bannerText')}</p>
        <div className="flex items-center gap-2 mt-2">
          <a
            href={donateUrl}
            target="_blank"
            rel="noopener noreferrer"
            onClick={() => close(true)}
            className="bg-primary text-on-primary px-3 py-1.5 rounded-full text-label-caps font-bold tracking-wider flex items-center gap-1 active:scale-95 transition-transform"
          >
            <Icon name="favorite" size={16} filled />
            {t('donate.cta')}
          </a>
          <button
            onClick={() => close(false)}
            className="px-3 py-1.5 rounded-full text-label-caps font-bold tracking-wider text-on-surface-variant hover:bg-surface-container"
          >
            {t('common.later')}
          </button>
        </div>
      </div>
      <button
        onClick={() => close(false)}
        className="p-1 rounded-full text-on-surface-variant hover:bg-surface-container active:scale-95"
        aria-label={t('common.close')}
      >
        <Icon name="close" size={18} />
      </button>
    </div>
  );
}
