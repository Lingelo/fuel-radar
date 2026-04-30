import { useEffect } from 'react';
import { createPortal } from 'react-dom';
import { timeAgo } from '../utils/fuel';

interface Props {
  onClose: () => void;
  lastUpdate?: string;
}

export function AboutModal({ onClose, lastUpdate }: Props) {
  useEffect(() => {
    const handleKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', handleKey);
    return () => document.removeEventListener('keydown', handleKey);
  }, [onClose]);

  return createPortal(
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" onClick={onClose}>
      <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" />
      <div
        className="relative max-w-md rounded-2xl bg-white p-6 shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <button
          onClick={onClose}
          className="absolute right-3 top-3 rounded-full p-1 text-gray-400 hover:bg-gray-100 hover:text-gray-600"
        >
          <svg className="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>

        <h2 className="mb-1 text-lg font-bold text-gray-900">Carburants France</h2>
        <p className="mb-4 text-xs text-gray-400">Trouvez le carburant le moins cher près de chez vous</p>

        <div className="space-y-3 text-sm leading-relaxed text-gray-600">
          <p>
            Dans un contexte de hausse des prix à la pompe, accentuée par les tensions
            géopolitiques internationales, chaque centime compte pour les automobilistes français.
          </p>
          <p>
            Cette application permet de comparer en temps réel les prix des carburants
            station par station, sur l'ensemble du territoire. Les données sont issues de
            l'open data du gouvernement (<strong>prix-carburants.gouv.fr</strong>) et mises
            à jour automatiquement toutes les 2 heures.
          </p>
          <p>
            Recherchez votre ville, choisissez votre carburant et trouvez la station la moins
            chère dans un rayon de 10 km.
          </p>
        </div>

        <div className="mt-4 rounded-lg bg-gray-50 px-3 py-2.5">
          <p className="mb-1.5 text-xs font-semibold text-gray-700">Sources de données</p>
          <ul className="space-y-1 text-xs text-gray-500">
            <li>
              <a href="https://donnees.roulez-eco.fr/opendata/instantane" target="_blank" rel="noopener noreferrer" className="text-primary hover:underline">prix-carburants.gouv.fr</a>
              {' '}— Prix des carburants en temps réel
            </li>
            <li>
              <a href="https://data.ademe.fr/datasets/ademe-car-labelling" target="_blank" rel="noopener noreferrer" className="text-primary hover:underline">ADEME Car Labelling</a>
              {' '}— Consommation des véhicules neufs
            </li>
            <li>
              <a href="https://www.eea.europa.eu/en/datahub/datahubitem-view/fa8b1229-3db6-495d-b18e-9c9b3267c02b" target="_blank" rel="noopener noreferrer" className="text-primary hover:underline">EEA CO2 Monitoring</a>
              {' '}— Emissions CO2 des véhicules immatriculés en France
            </li>
          </ul>
        </div>

        {lastUpdate && (
          <div className="mt-5 flex items-center gap-2 rounded-lg bg-primary/10 px-3 py-2 text-xs text-primary">
            <svg className="h-4 w-4 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <span>Dernière mise à jour : <strong>{timeAgo(lastUpdate)}</strong></span>
          </div>
        )}

        <div className={`${lastUpdate ? 'mt-3' : 'mt-5'} flex items-center justify-between border-t border-gray-100 pt-4`}>
          <span className="text-[11px] text-gray-400">
            Projet open source — Données gouv.fr, ADEME, EEA
          </span>
          <a
            href="https://github.com/Lingelo/carburants-france"
            target="_blank"
            rel="noopener noreferrer"
            className="text-[11px] text-primary hover:underline"
          >
            GitHub
          </a>
        </div>
      </div>
    </div>,
    document.body,
  );
}
