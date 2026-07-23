---
date: 2026-05-22
topic: map-tiles-maptiler-french-labels
---

# Migration des tuiles vers MapTiler SDK (vector) avec labels français globaux

## Summary

Remplacer le fond de carte CartoDB Positron actuel par MapTiler vector tiles (style "Streets v2" ou équivalent), affichées via le plugin `@maptiler/leaflet-maptilersdk` pour conserver l'API Leaflet existante. Forcer la localisation française des labels (pays, villes, régions) via `language: 'fr'`. Validation par prototype local avant merge.

---

## Problem Frame

L'utilisateur perçoit le rendu actuel (CartoDB Positron via `react-leaflet` + `L.tileLayer`) comme insuffisamment proche du look familier "Google Maps" qu'il vise esthétiquement. La carte affiche en outre les pays étrangers dans leur langue locale (Deutschland, España, Italia, etc.), comportement standard des tuiles raster basées sur le tag OSM `name`.

L'option naïve — basculer vers Google Maps Platform JS API — a été écartée : refonte tarifaire 2024-2025 imposant un compte de facturation avec CB obligatoire, clé API exposée côté client sur GitHub Pages (pas de backend pour proxifier), risque de quota brûlé par scraping malgré la restriction HTTP referer, incompatible avec la prémisse "sans payer un rond". Google Maps Embed API (iframe gratuit) est techniquement incompatible avec le clustering / pins custom de `MapScreen.tsx`.

La motivation explicite est **purement esthétique** : pas de besoin de POI commerciaux Google, pas de Street View, pas de 3D. Seul le rendu visuel et la francisation des labels comptent.

---

## Requirements

**Rendu visuel et localisation**
- R1. Le fond de carte affiche un style visuellement proche de Google Maps (couleurs chaleureuses, hiérarchie de routes claire, POI génériques visibles).
- R2. Les labels (pays, villes, régions, départements) sont affichés en français quel que soit le niveau de zoom et la zone géographique visible, y compris pour les pays frontaliers (Allemagne, Espagne, Italie, Suisse, Belgique, Royaume-Uni).
- R3. L'attribution légale du fournisseur de tuiles (MapTiler + OpenStreetMap) est affichée sur la carte conformément aux conditions d'utilisation.

**Préservation des fonctionnalités existantes**
- R4. Le clustering des stations (`leaflet.markercluster`) continue de fonctionner sans régression de comportement ou de perf perçue.
- R5. Les pins par tier de prix (vert→rouge), les popups `StationPopup`, le panneau latéral desktop / bottom sheet mobile, et le cercle de rayon de recherche (`SearchRadiusCircle`) continuent de fonctionner identiquement.
- R6. Les interactions existantes (recentrage utilisateur via `MapRecenter`, recherche d'adresse via `api-adresse.data.gouv.fr`, deeplink Google Maps depuis le détail station) restent intactes.

**Gestion de la clé API**
- R7. La clé API MapTiler est exposée côté client (contrainte GitHub Pages), restreinte par origin HTTP referer sur `lingelo.github.io`, et fournie via une variable d'environnement `VITE_*` injectée à build-time.
- R8. La clé n'est pas committée au repo. Un fichier `.env.example` documente la variable attendue. La clé prod est injectée via GitHub Actions secrets.

**Validation locale avant adoption**
- R9. Un prototype fonctionnel doit tourner en local sur la branche de feature avant tout merge sur `main`, permettant à l'utilisateur de juger visuellement le rendu sur sa région d'usage réelle et de mesurer la perf sur mobile.

---

## Acceptance Examples

- AE1. **Couvre R2.** Étant donné une vue de carte dézoomée affichant l'Europe de l'Ouest, quand l'utilisateur observe les labels des pays frontaliers de la France, alors il voit "Allemagne", "Espagne", "Italie", "Belgique", "Suisse", "Royaume-Uni" (pas "Deutschland", "España", etc.).
- AE2. **Couvre R4, R5.** Étant donné une vue de carte zoomée sur Paris avec 200+ stations dans le viewport, quand l'utilisateur zoome/dézoome, alors les clusters se forment et se décomposent comme aujourd'hui, et les popups au clic affichent le même contenu `StationPopup`.
- AE3. **Couvre R7, R8.** Étant donné un build de production sans la variable d'environnement `VITE_MAPTILER_KEY` définie, quand le build est exécuté, alors une erreur explicite est levée à build-time (pas un échec silencieux à runtime).

---

## Success Criteria

- L'utilisateur (toi) considère subjectivement que le rendu est "proche Google Maps" et préférable au rendu actuel.
- Aucune régression mesurable sur le clustering, les popups, ou les interactions de la carte.
- La carte affiche tous les labels en français sur les territoires hors France.
- Le build de prod GitHub Actions reste vert avec la clé MapTiler injectée via secret.
- Coût mensuel reste à 0 € dans des conditions de trafic actuelles (largement sous les 100k tile loads/mois du free tier MapTiler).

---

## Scope Boundaries

- **Hors scope :** intégration directe de Google Maps Platform JS API (refonte tarifaire, CB obligatoire, risque financier — incompatible avec la prémisse).
- **Hors scope :** Google Maps Embed API en iframe (incompatible avec clustering et pins custom).
- **Hors scope :** refonte vers `maplibre-gl-js` direct ou `react-map-gl` (effort disproportionné — le plugin `leaflet-maptilersdk` permet de garder l'API Leaflet).
- **Hors scope :** intégration de Street View, vues 3D, POI commerciaux Google, ou tout enrichissement de données non-stations.
- **Hors scope :** changement du provider de geocoding pour la recherche d'adresse (`api-adresse.data.gouv.fr` reste utilisé).
- **Hors scope :** modification du style de fond sur l'écran StationDetailScreen header carte (s'il diffère du MapScreen — à vérifier en planning).

---

## Key Decisions

- **Provider de tuiles : MapTiler Cloud.** Rationale : free tier 100k tile loads/mois sans CB requise, style "Streets v2" visuellement proche Google, localisation française documentée via SDK.
- **Rendu : vector tiles via MapTiler SDK (pas raster).** Rationale : seules les vector tiles supportent la localisation forcée des labels via `language: 'fr'`. Les tuiles raster (Carto, MapTiler raster, Stadia raster) conservent les noms OSM locaux — incompatible avec R2.
- **Pont vers Leaflet : plugin officiel `@maptiler/leaflet-maptilersdk`.** Rationale : préserve `MarkerClusterGroup`, `Popup`, `Circle`, et toute la stack `react-leaflet` existante. Évite une réécriture vers `react-map-gl`.
- **Validation locale obligatoire.** Rationale : le rendu vector tiles (WebGL) a un profil de perf différent du raster CPU sur mobile bas de gamme. Risque non négligeable sur la cible (PWA carte avec clustering lourd).
- **Clé API exposée client + restriction par referer.** Rationale : architecture GitHub Pages sans backend, pas d'autre option. Acceptable pour app open-source non-commerciale avec quota strict (pas d'auto-upgrade payant).

---

## Dependencies / Assumptions

- **Dépendance :** plugin `@maptiler/leaflet-maptilersdk` compatible avec `react-leaflet@5.0.0` et `leaflet@1.9.4` actuels. À vérifier en planning (peerDependencies, intégration avec `MapContainer` de `react-leaflet`).
- **Dépendance :** MapTiler SDK supporte le param `language: 'fr'` pour TOUS les labels (pays, villes, régions, POI génériques) sur le style choisi — vérifié par la doc `docs.maptiler.com/leaflet/examples/map-language/`.
- **Hypothèse :** le trafic actuel de l'app reste très en dessous de 100k tile loads/mois (à confirmer si métriques disponibles).
- **Hypothèse :** la perf vector tiles sur mobile bas de gamme reste acceptable avec 200+ markers + clustering. À mesurer pendant la validation locale.
- **Hypothèse non vérifiée :** le rendu MapTiler vector "Streets v2" est subjectivement jugé "proche Google" par l'utilisateur. À valider visuellement sur le prototype local — si le style ne convient pas, MapTiler Cloud propose plusieurs styles alternatifs (Backdrop, Basic, Bright) qu'on peut tester sans changer la stack.

---

## Outstanding Questions

### Resolve Before Planning

- (Aucune — décisions produit verrouillées.)

### Deferred to Planning

- [Affects R4, R5][Technical] Le plugin `@maptiler/leaflet-maptilersdk` s'intègre-t-il proprement comme enfant de `<MapContainer>` de `react-leaflet`, ou faut-il bypasser le `MapContainer` et utiliser `L.map()` directement ? Impacte la structure de `MapScreen.tsx`.
- [Affects R1][User decision] Quel style MapTiler retenir parmi Streets v2 / Backdrop / Bright / Basic ? Décision à prendre visuellement sur le prototype local (cf. R9).
- [Affects R4][Needs research] Comportement de `leaflet.markercluster` avec un layer vector WebGL en dessous : interactions de zoom, recalcul des clusters, perf sur mobile. À mesurer en local.
- [Affects R8][Technical] Mécanisme exact d'injection de `VITE_MAPTILER_KEY` dans le workflow GitHub Actions existant.
- [Affects R3][Technical] L'attribution est-elle gérée automatiquement par le plugin ou faut-il la déclarer manuellement dans le composant ?
