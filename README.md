# FuelRadar — monorepo

Comparateur de prix des carburants (France, Espagne, Portugal) alimenté par les données
ouvertes officielles. Ce dépôt regroupe deux applications qui partagent la **même source de
données** : des fichiers JSON statiques régénérés toutes les 2 h et servis par GitHub Pages.

## Structure

| Chemin | Application |
|---|---|
| [`apps/web`](apps/web) | PWA React/TypeScript/Vite déployée sur GitHub Pages — voir [`apps/web/README.md`](apps/web/README.md) |
| `apps/android` | App Android native (Kotlin / Jetpack Compose) — *en cours* |

Le web ne modifie pas la data : il la récupère au runtime via `fetch()` sur
`https://lingelo.github.io/fuel-radar/data/*.json`. L'app Android consomme **les mêmes URLs**
(aucune data embarquée). Ces URLs sont un contrat stable — voir le plan
[`docs/plans/2026-07-22-004-feat-monorepo-android-native-app-plan.md`](docs/plans/2026-07-22-004-feat-monorepo-android-native-app-plan.md).

## Développement

```sh
# Web
cd apps/web && npm install && npm run dev

# Android (nécessite le SDK Android + JDK 17)
# cd apps/android && ./gradlew assembleDebug
```

## Déploiement

`apps/web` est déployé sur GitHub Pages via GitHub Actions
([`.github/workflows/deploy.yml`](.github/workflows/deploy.yml)) ; la data est rafraîchie toutes
les 2 h ([`.github/workflows/update-data.yml`](.github/workflows/update-data.yml)). Le
sous-chemin publié reste `https://lingelo.github.io/fuel-radar/`.
