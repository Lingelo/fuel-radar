/** Format a price with French decimal comma. e.g. 2.09 → "2,090" */
export function formatPrice(price: number, decimals = 3): string {
  return price.toFixed(decimals).replace('.', ',');
}

/** Format a price with currency symbol. e.g. 2.09 → "2,090 €" */
export function formatPriceEuro(price: number, decimals = 3): string {
  return `${formatPrice(price, decimals)} €`;
}

/** Format a price delta with sign. e.g. 0.022 → "+0,022 €", -0.015 → "-0,015 €" */
export function formatPriceDelta(delta: number, decimals = 3): string {
  const sign = delta > 0 ? '+' : '';
  return `${sign}${delta.toFixed(decimals).replace('.', ',')} €`;
}

/** Format a percentage with French decimal comma. e.g. 18.94 → "+18,9 %" */
export function formatPercent(value: number, decimals = 1): string {
  const sign = value > 0 ? '+' : '';
  return `${sign}${value.toFixed(decimals).replace('.', ',')} %`;
}
