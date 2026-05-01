/** Convert a 5-digit French postal code to its department code. */
export function getDepartment(cp: string): string {
  if (cp.startsWith('97')) return cp.slice(0, 3);
  if (cp.startsWith('20')) {
    const num = parseInt(cp, 10);
    return num < 20200 ? '2A' : '2B';
  }
  return cp.slice(0, 2);
}

/** Approx square covering a circle of given radius km around a coord. */
export function boundingBox(lat: number, lng: number, radiusKm: number) {
  const dLat = radiusKm / 111;
  const dLng = radiusKm / (111 * Math.cos((lat * Math.PI) / 180));
  return {
    minLat: lat - dLat,
    maxLat: lat + dLat,
    minLng: lng - dLng,
    maxLng: lng + dLng,
  };
}
