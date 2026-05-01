# Carburants France

PWA de comparaison des prix carburants en France, alimentée par les données ouvertes de [prix-carburants.gouv.fr](https://prix-carburants.gouv.fr).

Live : <https://angelolima.github.io/carburants-france/>

## Stack

- **React 19** + **TypeScript** + **Vite 7**
- **Tailwind CSS v4** (tokens Material 3)
- **Leaflet** + Marker Cluster (carte + pins par tier de prix)
- **vite-plugin-pwa** (installable, offline runtime caching)

## Données

Pipeline automatique lancé toutes les 2 h via GitHub Actions :

- `npm run data` télécharge l'archive instantanée du gouvernement, parse le XML, et écrit `public/data/departments/{dept}.json` + `public/data/meta.json`. Les marques (TotalEnergies, Leclerc, etc.) sont enrichies via Overpass / OpenStreetMap.
- `npm run history:bootstrap` génère l'historique national `public/data/history.json` et l'historique par station `public/data/history/{dept}.json`.
- `npm run history:daily` met à jour incrémentalement l'historique avec les prix du jour.

## Commandes

```sh
npm install
npm run dev            # dev server
npm run build          # build prod (TS check + vite build + PWA)
npm run data           # rafraîchit les prix
npm run history:daily  # ajoute les prix du jour à l'historique
npm run lint
```

## Déploiement

Push sur `main` → GitHub Actions :
1. `process-data.mjs` (prix instantanés)
2. `generate-history.mjs --bootstrap` (historique)
3. `vite build`
4. `actions/deploy-pages` vers GitHub Pages

`vite.config.ts` configure `base: '/carburants-france/'` pour le sous-chemin Pages.

## Écrans

- **Carte** — Leaflet + pins colorés par tier de prix (vert→rouge), cluster, panneau latéral desktop / bottom sheet mobile, recherche d'adresse via `api-adresse.data.gouv.fr`.
- **Stations** — liste triable par prix/distance, chips de carburant, badge « Le moins cher ».
- **Détail** — header carte, prix par carburant avec tendance 7 j, graphe en barres, deeplink Google Maps.
- **Favoris** — persistés en `localStorage`.
- **Tendances** — moyennes journalières nationales, multi-courbes 30 j / 90 j / 1 an.
- **Réglages** — écran de démarrage, unité, avertissement données anciennes.

Les filtres (carburant, rayon, tri, marques) sont également persistés en `localStorage`.
