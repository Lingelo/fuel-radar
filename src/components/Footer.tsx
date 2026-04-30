import { timeAgo } from '../utils/fuel';

interface Props {
  onShowAbout: () => void;
  onShowHistory: () => void;
  lastUpdate?: string;
  hasCity: boolean;
}

// R8 — minimal footer carrying the existing utility links + the
// anti-pastiche disclaimer ("site indépendant — non affilié à l'État
// français"). Keeps the conditional mobile visibility (hidden md:flex
// when a city is selected, so the bottom sheet has room).
export function Footer({ onShowAbout, onShowHistory, lastUpdate, hasCity }: Props) {
  return (
    <footer
      role="contentinfo"
      className={`absolute bottom-2 left-2 z-10 flex flex-wrap items-center gap-x-2 gap-y-1 rounded-lg bg-white/80 px-3 py-1.5 text-[11px] text-gray-500 shadow-sm backdrop-blur-sm ${hasCity ? 'hidden md:flex' : ''}`}
    >
      <button
        onClick={onShowAbout}
        className="font-medium text-gray-600 hover:text-gray-900 underline decoration-gray-300 underline-offset-2"
      >
        À propos
      </button>
      <span className="text-gray-300">·</span>
      <button
        onClick={onShowHistory}
        className="font-medium text-gray-600 hover:text-gray-900 underline decoration-gray-300 underline-offset-2"
      >
        Évolution des prix
      </button>
      <span className="text-gray-300">·</span>
      <span>Données gouv.fr</span>
      {lastUpdate && (
        <>
          <span className="text-gray-300">·</span>
          <span>MAJ {timeAgo(lastUpdate)}</span>
        </>
      )}
      <span className="text-gray-300">·</span>
      <span className="italic text-gray-400">Site indépendant — non affilié à l'État français</span>
    </footer>
  );
}
