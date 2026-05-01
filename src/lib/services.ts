/** Map raw gov service labels to a Material Symbols icon. */
export const SERVICE_ICONS: Record<string, string> = {
  'Boutique alimentaire': 'storefront',
  'Boutique non alimentaire': 'shopping_bag',
  'Restauration à emporter': 'fastfood',
  'Restauration sur place': 'restaurant',
  Bar: 'local_bar',
  'Station de gonflage': 'tire_repair',
  'Lavage automatique': 'local_car_wash',
  'Lavage manuel': 'local_car_wash',
  'DAB (Distributeur automatique de billets)': 'atm',
  Toilettes: 'wc',
  'Toilettes publiques': 'wc',
  'Vente de fioul domestique': 'local_shipping',
  'Vente de gaz domestique (Butane, Propane)': 'propane_tank',
  'Piste poids lourds': 'local_shipping',
  'Wifi': 'wifi',
  'Aire de camping-cars': 'rv_hookup',
  'Location de véhicule': 'directions_car',
  'Vente de pétrole lampant': 'oil_barrel',
  'Vente d\'additifs carburants': 'science',
  'Aire de stationnement / repos': 'park',
};

/** Fallback icon for any service we don't have a specific mapping for. */
export const DEFAULT_SERVICE_ICON = 'check_circle';

export function getServiceIcon(label: string): string {
  return SERVICE_ICONS[label] ?? DEFAULT_SERVICE_ICON;
}
