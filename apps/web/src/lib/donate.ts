/**
 * Lien de dons (Ko-fi), injecté au build via `VITE_DONATE_URL`
 * (variable de dépôt `DONATE_URL`, voir .github/workflows/deploy.yml).
 * Vide → la section « Soutenir » et le bandeau restent masqués.
 */
export const donateUrl: string = import.meta.env.VITE_DONATE_URL ?? '';

const KEY = 'fuelradar:donate';
const SESSION_KEY = 'fuelradar:donate-visit';

/** Visites avant la première invitation. */
const MIN_VISITS = 5;
/** Délai avant de reproposer après « Plus tard ». */
const SNOOZE_MS = 30 * 24 * 3600_000;
/** Délai avant de reproposer après un clic sur « Faire un don ». */
const THANKS_MS = 180 * 24 * 3600_000;

interface DonateState {
  visits: number;
  hiddenUntil: number;
}

function load(): DonateState {
  try {
    const raw = localStorage.getItem(KEY);
    if (raw) {
      const s = JSON.parse(raw) as Partial<DonateState>;
      return { visits: s.visits ?? 0, hiddenUntil: s.hiddenUntil ?? 0 };
    }
  } catch {
    // storage unavailable → defaults
  }
  return { visits: 0, hiddenUntil: 0 };
}

function save(s: DonateState) {
  try {
    localStorage.setItem(KEY, JSON.stringify(s));
  } catch {
    // ignore
  }
}

/**
 * Compte une visite (une seule par session navigateur) et indique si le
 * bandeau doit s'afficher.
 */
export function registerVisitAndShouldPrompt(): boolean {
  if (!donateUrl) return false;
  const s = load();
  let firstInSession = true;
  try {
    firstInSession = !sessionStorage.getItem(SESSION_KEY);
    sessionStorage.setItem(SESSION_KEY, '1');
  } catch {
    // ignore
  }
  if (!firstInSession) return false;
  s.visits += 1;
  save(s);
  return s.visits >= MIN_VISITS && Date.now() >= s.hiddenUntil;
}

export function snoozeDonatePrompt(donated = false) {
  const s = load();
  s.hiddenUntil = Date.now() + (donated ? THANKS_MS : SNOOZE_MS);
  save(s);
}
