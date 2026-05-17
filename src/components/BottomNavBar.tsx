import { useViewNav } from '../state/ViewContext';
import { Icon } from './Icon';

export function BottomNavBar() {
  const { view, goMap, goStations, goFavorites, goTrends, goSettings } = useViewNav();

  const tabClass = (active: boolean) =>
    [
      'flex flex-col items-center justify-center px-1 py-1 active:scale-90 transition-transform flex-1 min-w-0 max-w-[80px] h-14 rounded-xl',
      active
        ? 'text-on-primary-fixed-variant bg-primary-fixed'
        : 'text-on-surface-variant hover:text-primary',
    ].join(' ');

  const isMap = view.kind === 'map';
  const isStations = view.kind === 'stations';
  const isFav = view.kind === 'favorites';
  const isTrends = view.kind === 'trends';
  const isSettings = view.kind === 'settings';

  return (
    <nav className="md:hidden fixed bottom-0 left-0 w-full flex justify-around items-center gap-1 px-1 pb-safe h-[calc(4rem+env(safe-area-inset-bottom))] bg-surface-container-lowest z-[500] border-t border-outline-variant shadow-[0_-4px_6px_-1px_rgba(0,0,0,0.05)]">
      <button onClick={() => goMap()} className={tabClass(isMap)}>
        <Icon name="map" filled={isMap} />
        <span className="text-[10px] font-medium mt-1 max-w-full truncate">Carte</span>
      </button>
      <button onClick={goStations} className={tabClass(isStations)}>
        <Icon name="format_list_bulleted" filled={isStations} />
        <span className="text-[10px] font-medium mt-1 max-w-full truncate">Stations</span>
      </button>
      <button onClick={goFavorites} className={tabClass(isFav)}>
        <Icon name="star" filled={isFav} />
        <span className="text-[10px] font-medium mt-1 max-w-full truncate">Favoris</span>
      </button>
      <button onClick={goTrends} className={tabClass(isTrends)}>
        <Icon name="insights" filled={isTrends} />
        <span className="text-[10px] font-medium mt-1 max-w-full truncate">Tendances</span>
      </button>
      <button onClick={goSettings} className={tabClass(isSettings)}>
        <Icon name="settings" filled={isSettings} />
        <span className="text-[10px] font-medium mt-1 max-w-full truncate">Réglages</span>
      </button>
    </nav>
  );
}
