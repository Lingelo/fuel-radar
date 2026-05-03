import { useViewNav } from '../state/ViewContext';
import { InstallButton } from './InstallButton';

export function TopAppBar() {
  const { view, goMap, goStations, goFavorites, goTrends, goSettings } = useViewNav();

  const linkClass = (active: boolean) =>
    [
      'text-body-sm transition-colors px-3 py-2 rounded-md active:scale-95 duration-150',
      active
        ? 'text-primary font-semibold'
        : 'text-on-surface-variant hover:text-primary',
      'hover:bg-surface-container-low',
    ].join(' ');

  return (
    <header className="flex justify-between items-center gap-2 px-4 h-16 w-full top-0 left-0 bg-surface-container-lowest border-b border-outline-variant z-[500] fixed">
      <button onClick={() => goMap()} className="flex items-center gap-sm cursor-pointer active:scale-95 transition-transform min-w-0">
        <img src={`${import.meta.env.BASE_URL}icon.svg`} alt="" className="w-7 h-7 shrink-0" />
        <h1 className="text-xl font-bold text-on-surface tracking-tight truncate">Carburants France</h1>
      </button>

      <nav className="hidden md:flex items-center gap-lg">
        <button onClick={() => goMap()} className={linkClass(view.kind === 'map')}>
          Carte
        </button>
        <button onClick={goStations} className={linkClass(view.kind === 'stations')}>
          Stations
        </button>
        <button onClick={goFavorites} className={linkClass(view.kind === 'favorites')}>
          Favoris
        </button>
        <button onClick={goTrends} className={linkClass(view.kind === 'trends')}>
          Tendances
        </button>
        <button onClick={goSettings} className={linkClass(view.kind === 'settings')}>
          Réglages
        </button>
      </nav>

      <InstallButton />
    </header>
  );
}
