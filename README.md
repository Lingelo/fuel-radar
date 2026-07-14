# FuelRadar

PWA de comparaison des prix carburants en France, en Espagne et au Portugal, alimentée par les données ouvertes de [prix-carburants.gouv.fr](https://prix-carburants.gouv.fr), du [Ministerio para la Transición Ecológica](https://geoportalgasolineras.es) (Espagne) et de la [DGEG](https://precoscombustiveis.dgeg.gov.pt) (Portugal).

Live : <https://lingelo.github.io/fuel-radar/>

## Stack

- **React 19** + **TypeScript** + **Vite 7**
- **Tailwind CSS v4** (tokens Material 3)
- **Leaflet** + Marker Cluster (carte + pins par tier de prix)
- **vite-plugin-pwa** (installable, offline runtime caching)

## Données

Pipeline automatique lancé toutes les 2 h via GitHub Actions :

- `npm run data:fr` télécharge l'archive instantanée du gouvernement français, parse le XML, et écrit `public/data/departments/{dept}.json` + `public/data/meta.json`. Les marques (TotalEnergies, Leclerc, etc.) sont enrichies via Overpass / OpenStreetMap.
- `npm run data:iberia` récupère les stations espagnoles (API REST du Ministerio, groupées par province → `ES-{code}.json`) et portugaises (API DGEG, groupées par district → `PT-{slug}.json`), avec des ids décalés (+100M / +200M) pour ne jamais entrer en collision avec les ids français. `npm run data` enchaîne les deux.
- `npm run history:bootstrap` génère l'historique national `public/data/history.json` et l'historique par station `public/data/history/{dept}.json`.
- `npm run history:daily` met à jour incrémentalement l'historique avec les prix du jour.

## Commandes

```sh
npm install
npm run dev            # dev server
npm run build          # build prod (TS check + vite build + PWA)
npm run data           # rafraîchit les prix (France + Espagne + Portugal)
npm run history:daily  # ajoute les prix du jour à l'historique
npm run lint
```

## Déploiement

Push sur `main` → GitHub Actions :
1. `process-data.mjs` (prix instantanés France)
2. `process-data-iberia.mjs` (prix Espagne + Portugal)
3. `generate-history.mjs --bootstrap` (historique)
4. `vite build`
5. `actions/deploy-pages` vers GitHub Pages

`vite.config.ts` configure `base: '/fuel-radar/'` pour le sous-chemin Pages.

## Écrans

- **Carte** — Leaflet + pins colorés par tier de prix (vert→rouge), cluster, panneau latéral desktop / bottom sheet mobile, recherche d'adresse via `api-adresse.data.gouv.fr` (France) + `photon.komoot.io` (Espagne / Portugal).
- **Stations** — liste triable par prix/distance, chips de carburant, badge « Le moins cher ».
- **Détail** — header carte, prix par carburant avec tendance 7 j, graphe en barres, deeplink Google Maps.
- **Favoris** — persistés en `localStorage`.
- **Tendances** — moyennes journalières nationales, multi-courbes 30 j / 90 j / 1 an.
- **Réglages** — écran de démarrage, unité, avertissement données anciennes.

Les filtres (carburant, rayon, tri, marques) sont également persistés en `localStorage`.
