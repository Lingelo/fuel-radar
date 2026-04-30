import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { timeAgo } from '../utils/fuel';

interface Props {
  onClose: () => void;
  lastUpdate?: string;
}

export function AboutModal({ onClose, lastUpdate }: Props) {
  return (
    <Dialog open onOpenChange={(open) => { if (!open) onClose(); }}>
      <DialogContent className="max-w-3xl">
        <DialogHeader>
          <DialogTitle>Carburants France</DialogTitle>
          <DialogDescription>Trouvez le carburant le moins cher près de chez vous</DialogDescription>
        </DialogHeader>

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
          <p className="mb-1.5 text-xs font-semibold text-gray-700">Source de données</p>
          <ul className="space-y-1 text-xs text-gray-500">
            <li>
              <a href="https://donnees.roulez-eco.fr/opendata/instantane" target="_blank" rel="noopener noreferrer" className="text-primary hover:underline">prix-carburants.gouv.fr</a>
              {' '}— Prix des carburants en temps réel
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
            Projet open source — Données gouv.fr
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
      </DialogContent>
    </Dialog>
  );
}
