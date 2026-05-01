import { useEffect, useState } from 'react';

/** Subset of the BeforeInstallPromptEvent we use. */
interface BeforeInstallPromptEvent extends Event {
  readonly platforms: ReadonlyArray<string>;
  readonly userChoice: Promise<{ outcome: 'accepted' | 'dismissed'; platform: string }>;
  prompt(): Promise<void>;
}

export type InstallPlatform =
  | 'native-prompt'    // Chromium captured beforeinstallprompt; we can call prompt()
  | 'ios-safari'       // iOS Safari → Share + Add to Home Screen
  | 'in-app-webview'   // Facebook/Instagram/Gmail in-app browser
  | 'unsupported'      // Already installed or browser can't install
  | 'generic';         // Firefox / desktop without prompt → use browser menu

interface State {
  /** True when the OS already runs the app from the home screen. */
  installed: boolean;
  /** What flow we should show the user. */
  platform: InstallPlatform;
  /** Trigger the native install dialog if available. Returns true if accepted. */
  install: () => Promise<boolean>;
}

function detectPlatform(): InstallPlatform {
  if (typeof window === 'undefined') return 'unsupported';

  // Already running standalone
  const standalone =
    window.matchMedia?.('(display-mode: standalone)').matches ||
    (navigator as Navigator & { standalone?: boolean }).standalone === true;
  if (standalone) return 'unsupported';

  const ua = navigator.userAgent;

  // Detect in-app webviews (Facebook, Instagram, Gmail, LinkedIn).
  const isWebview = /FBAN|FBAV|Instagram|Twitter|Line|MicroMessenger|GSA|LinkedIn/.test(ua);
  if (isWebview) return 'in-app-webview';

  // iOS Safari (no beforeinstallprompt support yet)
  const isIOS = /iPad|iPhone|iPod/.test(ua) && !('MSStream' in window);
  if (isIOS) return 'ios-safari';

  return 'generic';
}

export function useInstallPrompt(): State {
  const [deferred, setDeferred] = useState<BeforeInstallPromptEvent | null>(null);
  const [platform, setPlatform] = useState<InstallPlatform>(() => detectPlatform());
  const [installed, setInstalled] = useState(() => detectPlatform() === 'unsupported');

  useEffect(() => {
    const onBeforeInstall = (e: Event) => {
      e.preventDefault();
      setDeferred(e as BeforeInstallPromptEvent);
      setPlatform('native-prompt');
    };
    const onInstalled = () => {
      setInstalled(true);
      setDeferred(null);
      setPlatform('unsupported');
    };
    window.addEventListener('beforeinstallprompt', onBeforeInstall);
    window.addEventListener('appinstalled', onInstalled);
    return () => {
      window.removeEventListener('beforeinstallprompt', onBeforeInstall);
      window.removeEventListener('appinstalled', onInstalled);
    };
  }, []);

  const install = async () => {
    if (!deferred) return false;
    try {
      await deferred.prompt();
      const choice = await deferred.userChoice;
      setDeferred(null);
      if (choice.outcome === 'accepted') {
        setInstalled(true);
        setPlatform('unsupported');
        return true;
      }
      return false;
    } catch {
      return false;
    }
  };

  return { installed, platform, install };
}
