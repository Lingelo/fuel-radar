---
date: 2026-07-22
artifact_contract: ce-unified-plan/v1
artifact_readiness: implementation-ready
product_contract_source: ce-brainstorm
execution: code
topic: monorepo-android-native-app
---

# Monorepo + app Android native FuelRadar - Plan

> **Product Contract preservation** — Product Contract inchangé (R1–R13, décisions et
> périmètre conservés du brainstorm du 2026-07-22). Cette révision ajoute uniquement le
> contrat de planning (HOW) : décisions techniques, units, vérification, DoD.

## Goal Capsule

**Objectif** — Restructurer le dépôt `carburants-france` en monorepo à deux applications :
le web React existant (déplacé, **fonctionnellement inchangé**) et une **nouvelle app Android
native** (Kotlin / Jetpack Compose) qui reproduit les 7 maquettes Stitch et consomme la même
data JSON statique déjà servie par GitHub Pages. Aucun backend n'est créé.

**Autorité produit** — Angélo LIMA (mainteneur solo). Décisions produit tranchées en brainstorm
le 2026-07-22.

**Bloquants ouverts**
- 🔴 **Clé Google Maps API Android** — nécessite un projet Google Cloud avec compte de
  facturation (CB attachée), à créer **plus tard** par le mainteneur. **Bloque uniquement
  l'écran Carte (U8)** et la soumission Play Store (U9). Les units U1–U7 n'en dépendent pas.
- ⚠️ **Contrat GH Pages à préserver** — le déplacement du web sous `apps/web/` ne doit pas
  changer l'URL publiée (`https://lingelo.github.io/fuel-radar/`) ni les URLs de data
  (`.../fuel-radar/data/*.json`), qui sont le contrat consommé par la native.

---

## Product Contract

### Problem Frame

Le projet est aujourd'hui une PWA React (`FuelRadar`) déployée sur GitHub Pages. Contrairement
à la perception initiale, **le web n'embarque pas la data** : il fait des `fetch()` au runtime
sur des fichiers JSON statiques (`public/data/`, ~40 Mo) régénérés toutes les 2 h par GitHub
Actions et servis tels quels par GH Pages. Il n'existe **aucun backend ni web service** ;
`src/lib/services.ts` n'appelle aucune API propriétaire.

Le mainteneur veut une **app Android native** distincte qui reprend l'identité et les écrans des
maquettes Stitch (mêmes tokens Material 3 que le web) et va chercher sa data à distance — ce que
le web fait déjà. Les URLs statiques GH Pages sont donc **déjà l'API de données** ; la valeur
ajoutée de la native est un **rendu et une distribution natifs** (Play Store, feel natif, carte
Google Maps), pas l'accès data.

### Requirements

**Monorepo & préservation du web**
- R1. Dépôt réorganisé en monorepo à découpage par dossiers : web sous `apps/web/`, native sous
  `apps/android/`. Pas d'outil de monorepo (Nx/Turborepo).
- R2. Le web reste **fonctionnellement identique** : aucune régression de comportement, build ou
  rendu. Seul son emplacement change.
- R3. Le déploiement GH Pages continue de publier à la même URL (`.../fuel-radar/`) : workflows
  adaptés au nouveau dossier, `base: '/fuel-radar/'` inchangé.
- R4. Les URLs de data publiques (`https://lingelo.github.io/fuel-radar/data/*.json`) restent
  strictement stables — contrat consommé par la native.

**App Android native**
- R5. App native **Kotlin / Jetpack Compose**, reproduisant les 7 écrans Stitch : Carte, Liste,
  Détail, Favoris, Tendances, Réglages.
- R6. Parité fonctionnelle complète dès la v1 (7 écrans).
- R7. La native **ne bundle pas la data** : récupération par `fetch` HTTP sur les mêmes URLs
  statiques GH Pages, par département à la demande.
- R8. Recherche d'adresse / géocodage via les mêmes APIs gratuites sans clé :
  `api-adresse.data.gouv.fr` (France) et `photon.komoot.io` (Espagne/Portugal).
- R9. Cache local de la dernière data récupérée par département pour une résilience hors-ligne
  de base.
- R10. Favoris et filtres (carburant, rayon, tri, marques) persistés localement.

**Carte**
- R11. Écran Carte via **Google Maps SDK for Android** (look Google-like + labels FR natifs),
  clustering des stations et pins colorés par tier de prix (vert→rouge).
- R12. Clé Maps restreinte par *package name + empreinte SHA-1*, jamais committée.

**Distribution**
- R13. App conçue pour le **Google Play Store** : signature release, fiche store, politique de
  confidentialité, formulaire Data Safety.

### Primary Flow (native)

1. Ouverture → écran de démarrage configuré (Carte par défaut).
2. Récupération de `meta.json` + `dept-bbox.json`, puis data du/des département(s) pertinents via HTTP.
3. Recherche d'adresse ou géolocalisation → stations proches (carte + liste).
4. Ouverture d'une station → détail : prix par carburant, tendance 7 j, deeplink Google Maps.
5. Ajout de favoris / réglage des filtres → persistés localement.
6. Sans réseau → dernière data en cache par département consultable (R9).

### Acceptance Examples

- **AE1 — GH Pages intact** : après restructuration, un push sur `main` redéploie le web à
  `.../fuel-radar/` sans changement d'URL ni régression ; `.../data/75.json` répond toujours.
- **AE2 — Data distante native** : sur appareil neuf, l'app affiche les prix d'un département
  sans data embarquée dans l'APK (l'APK ne contient aucun JSON de `public/data`).
- **AE3 — Parité écrans** : les 7 écrans sont navigables et reprennent visuellement les maquettes
  Stitch (tokens Material 3, Inter, price-display).
- **AE4 — Carte gated** : sans clé Maps, U1–U7 fonctionnent ; seule la Carte est en attente.
- **AE5 — Offline** : après un premier chargement en ligne puis passage hors-ligne, la liste d'un
  département déjà consulté reste affichée.

### Out of Scope

- Toute modification fonctionnelle du web.
- La migration MapTiler du web (déjà partiellement câblée côté web ; sujet séparé —
  `docs/brainstorms/2026-05-22-map-tiles-maptiler-french-labels-requirements.md`).
- iOS et cross-platform.
- Création d'une API / backend / base de données.

---

## Planning Contract

### Key Technical Decisions

- **KTD1 — App native Kotlin/Compose** *(session-settled: user-directed — chosen over wrapper PWA
  et cross-platform : vrai feel natif + design Stitch, web non touché ; trade-off assumé ~10-50×
  l'effort d'un wrapper)*. Instancie la décision de périmètre du brainstorm.
- **KTD2 — Monorepo par découpage de dossiers, sans outil dédié** *(session-settled:
  user-directed)*. npm et Gradle ne partagent pas de build ; un orchestrateur serait de la
  complexité sans bénéfice (YAGNI).
- **KTD3 — Data via URLs statiques GH Pages, pas de backend** *(session-settled: user-directed —
  chosen over construire une API)*. L'infra data existe déjà ; CORS non-applicable en natif.
- **KTD4 — Google Maps SDK Android via `maps-compose` 8.3.1** *(session-settled: user-directed —
  chosen over MapLibre+MapTiler et CartoDB)*. `com.google.maps.android:maps-compose:8.3.1` +
  `maps-compose-utils:8.3.1` (composable `Clustering` fourni). Vérifié : doc officielle à jour
  2026-07-10.
- **KTD5 — Publication Play Store** *(session-settled: user-directed)*. Localisation **précise
  foreground only** (pas de background), conforme à la maj Play du 15/04/2026 (portée minimale).
- **KTD6 — Réimplémenter la logique métier en Kotlin** (tiers de prix, distance, sélection de
  département via bbox, format). Aucun partage de code TS↔Kotlin : runtimes disjoints, une seule
  plateforme native → une lib partagée serait prématurée. Mirroir des sources web
  `src/lib/priceColor.ts`, `src/lib/distance.ts`, `src/lib/deptIndex.ts`, `src/lib/department.ts`,
  `src/lib/format.ts`.
- **KTD7 — Adapter les workflows via `defaults.run.working-directory: apps/web`** plutôt que de
  réécrire chaque chemin de script. Diff minimal, moins de risque de régression CI. Ajuster en plus
  les chemins **relatifs à la racine** qui ne suivent pas le working-directory : `path:` de
  `upload-pages-artifact` → `apps/web/dist`, et le `git add` de `update-data.yml` →
  `apps/web/brands-cache.json apps/web/public/data/history-countries.json`.
- **KTD8 — Stack data Android : Retrofit + OkHttp + kotlinx.serialization**, images via Coil.
  L'**offline (R9)** s'appuie sur le **cache disque OkHttp** avec repli « servir le périmé en cas
  d'échec réseau », équivalent natif du `NetworkFirst` du web (`vite.config.ts`).
- **KTD9 — `minSdk = 26`** (Android 8.0). Hypothèse ; couvre >95 % du parc, APIs modernes.
- **KTD10 — Base URL data en `BuildConfig`** : `https://lingelo.github.io/fuel-radar/data/`.
  Découple la native du contrat GH Pages (R4) sans hardcode dispersé.

### High-Level Technical Design

**Structure monorepo cible**

```
carburants-france/                 (racine du dépôt = repo GH Pages, inchangé comme origine)
├─ apps/
│  ├─ web/                         ← tout le contenu web actuel déplacé ici
│  │  ├─ src/  public/  scripts/  index.html  package.json  vite.config.ts …
│  └─ android/                     ← nouveau projet Gradle
│     ├─ settings.gradle.kts  build.gradle.kts
│     └─ app/
│        ├─ build.gradle.kts       (BuildConfig: DATA_BASE_URL, MAPS_API_KEY)
│        └─ src/main/…             (Compose, navigation, data, écrans)
├─ .github/workflows/              (deploy.yml, update-data.yml — adaptés)
├─ docs/  README.md                (README racine = index monorepo)
```

**Flux data natif** (mirroir de `src/hooks/useNearbyStations.ts` + `src/lib/data.ts`)

```
Localisation/adresse ──► sélection dept via dept-bbox.json ──► GET data/departments/{dept}.json
        │                        (KTD6, mirroir deptIndex.ts)             │
        ▼                                                                 ▼
  géocodage (R8)                                              OkHttp Cache (KTD8, R9)
  api-adresse / photon                                        repli "stale on failure"
```

**Contrat CI préservé** — `deploy.yml` et `update-data.yml` : ajout de
`defaults.run.working-directory: apps/web` ; `upload-pages-artifact` `path: apps/web/dist` ;
`git add` re-préfixé. `base: '/fuel-radar/'` de `vite.config.ts` **inchangé** → URL publiée et
URLs de data identiques (R3, R4).

---

## Implementation Units

### Phase 1 — Monorepo & GH Pages (web fonctionnellement intact)

### U1. Déplacer le web sous `apps/web/`

- **Goal** — Restructurer l'arbre en monorepo sans modifier le comportement du web.
- **Requirements** — R1, R2.
- **Dependencies** — aucune.
- **Files** — déplacement (git mv) de `src/`, `public/`, `scripts/`, `index.html`,
  `package.json`, `package-lock.json`, `vite.config.ts`, `tsconfig*.json`, `eslint.config.js`,
  `brands-cache.json` → sous `apps/web/`. Nouveau `README.md` racine (index monorepo) ; conserver
  ou déplacer l'ancien README sous `apps/web/`.
- **Approach** — `git mv` pour préserver l'historique. Vérifier qu'aucun chemin **absolu à la
  racine** ne subsiste dans le code web (les scripts data écrivent en relatif dans `public/data`,
  à confirmer). `vite.config.ts` inchangé (`base` conservé). Ne pas toucher les workflows dans
  cette unit — ils sont adaptés en U2 et **doivent atterrir dans le même commit/PR que U1** pour
  ne jamais laisser `main` dans un état où le déploiement est cassé.
- **Execution note** — Après déplacement, `cd apps/web && npm ci && npm run build` doit produire
  `apps/web/dist` identique à avant (smoke build, pas de test unitaire).
- **Patterns to follow** — structure `apps/*` conventionnelle ; aucun outil monorepo (KTD2).
- **Test scenarios** — `Test expectation: none — déplacement mécanique`. Vérification : build local
  OK, `git log --follow apps/web/src/App.tsx` montre l'historique préservé.
- **Verification** — `npm run build` depuis `apps/web` réussit ; `dist` contient `index.html` avec
  les chemins `/fuel-radar/…`.

### U2. Adapter les workflows GH Pages au nouveau chemin

- **Goal** — Garder le déploiement et le cron data verts après le déplacement (AE1).
- **Requirements** — R3, R4.
- **Dependencies** — U1 (même commit/PR).
- **Files** — `.github/workflows/deploy.yml`, `.github/workflows/update-data.yml`.
- **Approach** — Ajouter `defaults: { run: { working-directory: apps/web } }` au job (couvre
  `npm ci`, `npm run build`, `node scripts/*.mjs`). Corriger les chemins **relatifs à la racine**
  non couverts par `working-directory` (KTD7) : `upload-pages-artifact` → `path: apps/web/dist` ;
  `setup-node` `cache: 'npm'` → ajouter `cache-dependency-path: apps/web/package-lock.json` ;
  dans `update-data.yml`, `git add apps/web/brands-cache.json apps/web/public/data/history-countries.json`.
  Vérifier que le `git commit`/`push` de retour vise toujours les bons fichiers.
- **Execution note** — Valider via `workflow_dispatch` sur une **branche** avant merge sur `main`
  (le push sur `main` auto-déploie) ; contrôler que l'artefact Pages contient bien `data/` et que
  l'URL publiée reste `.../fuel-radar/`.
- **Patterns to follow** — steps existants inchangés hormis chemins ; injection `VITE_MAPTILER_KEY`
  au step Build conservée telle quelle.
- **Test scenarios**
  - CI : un run `workflow_dispatch` de `deploy.yml` sur branche termine en succès et publie un
    artefact contenant `index.html` + `data/meta.json`. `Covers AE1.`
  - CI : `update-data.yml` régénère la data, commite `apps/web/brands-cache.json` et
    `apps/web/public/data/history-countries.json` si changés, sans erreur de chemin.
  - Régression URL : `curl https://lingelo.github.io/fuel-radar/data/meta.json` répond 200 JSON
    après déploiement. `Covers R4.`
- **Verification** — Déploiement branche vert ; URLs data inchangées.

### Phase 2 — Socle app Android native

### U3. Bootstrap du projet Android (Gradle, Compose, navigation, thème)

- **Goal** — Squelette compilable : projet Gradle, thème Material 3 depuis les tokens Stitch,
  navigation à 5 onglets (bottom nav) vide.
- **Requirements** — R5, R6.
- **Dependencies** — U1.
- **Files** (nouveaux) — `apps/android/settings.gradle.kts`, `apps/android/build.gradle.kts`,
  `apps/android/gradle/libs.versions.toml`, `apps/android/app/build.gradle.kts`,
  `apps/android/app/src/main/AndroidManifest.xml`,
  `apps/android/app/src/main/java/.../MainActivity.kt`,
  `.../ui/theme/{Color,Type,Theme}.kt`, `.../ui/navigation/AppNav.kt`.
- **Approach** — Compose BOM, Material 3, Navigation Compose. Traduire `DESIGN.md` (export Stitch)
  en `Color.kt`/`Type.kt` : palette teal, Inter, token `price-display`, `tabular-nums` pour les
  prix. Bottom nav 5 onglets (Carte, Stations, Favoris, Tendances, Réglages), écrans placeholder.
  `BuildConfig` : `DATA_BASE_URL` (KTD10) et `MAPS_API_KEY` (lu depuis `local.properties`/secret,
  jamais committé — R12). `minSdk = 26` (KTD9).
- **Execution note** — Config/scaffolding : privilégier une vérif de build + lancement émulateur
  plutôt que des tests unitaires.
- **Patterns to follow** — tokens de `apps/web/src/index.css` (source de vérité couleurs) recoupés
  avec `DESIGN.md`.
- **Test scenarios** — `Test expectation: none — scaffolding/config`. Smoke : l'app démarre sur
  l'émulateur, la bottom nav bascule entre 5 écrans vides.
- **Verification** — `./gradlew :app:assembleDebug` réussit ; APK debug s'installe et affiche la
  navigation.

### U4. Couche données : modèles, client HTTP, géocodage, cache offline

- **Goal** — Récupérer et parser la data GH Pages, sélectionner le bon département, résilience
  offline.
- **Requirements** — R7, R8, R9.
- **Dependencies** — U3.
- **Files** (nouveaux) — `.../data/model/{Station,MetaData,StationHistory,DeptBbox}.kt`,
  `.../data/net/FuelApi.kt` (Retrofit), `.../data/net/NetworkModule.kt` (OkHttp + cache),
  `.../data/DeptIndex.kt` (mirroir bbox), `.../data/geo/Geocoder.kt`,
  `.../data/StationRepository.kt`.
- **Approach** — Modèles kotlinx.serialization mirroir de `apps/web/src/types/index.ts`
  (`Station`, `MetaData`, `StationHistoryData`). Retrofit sur `DATA_BASE_URL` : endpoints
  `meta.json`, `dept-bbox.json`, `departments/{dept}.json`, `history/{dept}.json`,
  `history.json`, `history-countries.json`. OkHttp `Cache` (disque) + intercepteur repli
  `onlyIfCached` sur `IOException` (KTD8, R9). `DeptIndex` : depuis `dept-bbox.json`, résoudre
  le(s) département(s) couvrant une position/viewport — mirroir de `apps/web/src/lib/deptIndex.ts`
  et `apps/web/src/hooks/useNearbyStations.ts`. Géocodage : `api-adresse.data.gouv.fr` (FR) +
  `photon.komoot.io` (ES/PT), sans clé (R8), mirroir de `apps/web/src/lib/geocode.ts`.
- **Patterns to follow** — logique de `apps/web/src/lib/data.ts`, `deptIndex.ts`, `geocode.ts`,
  `distance.ts` ; comportement de cache calqué sur `runtimeCaching` de `apps/web/vite.config.ts`.
- **Test scenarios**
  - Parsing : un JSON département réel désérialise en `List<Station>` (champs prix/marque/coords).
  - Sélection dept : une coordonnée dans le 75 résout le fichier `75.json` ; une coordonnée hors
    France (Espagne) résout `ES-{code}.json`. `Covers R7.`
  - Offline : après un GET réussi mis en cache, un second appel réseau en échec renvoie la donnée
    en cache (repli `onlyIfCached`). `Covers AE5 / R9.`
  - Erreur : un 404 département renvoie liste vide sans crash (mirroir du comportement web).
  - Géocodage : une requête d'adresse FR renvoie des coordonnées ; un échec réseau renvoie une
    erreur typée, pas un crash.
- **Verification** — Tests unitaires repo/parse/dept-index verts ; appel réel en émulateur affiche
  des stations pour une position donnée.

### U5. Persistance locale (favoris + filtres)

- **Goal** — Mémoriser favoris et filtres entre sessions.
- **Requirements** — R10.
- **Dependencies** — U3.
- **Files** (nouveaux) — `.../data/prefs/FavoritesStore.kt`, `.../data/prefs/FiltersStore.kt`
  (DataStore), `.../data/prefs/models.kt`.
- **Approach** — Jetpack DataStore (Preferences ou Proto) — équivalent natif du `localStorage`
  web (`apps/web/src/state/FavoritesContext.tsx`, `FiltersContext.tsx`). Favoris = set d'ids de
  stations ; filtres = carburant, rayon, tri, marques sélectionnées.
- **Patterns to follow** — clés et sémantique de `apps/web/src/state/FavoritesContext.tsx` et
  `FiltersContext.tsx`.
- **Test scenarios**
  - Un favori ajouté puis relu après recréation du store est présent. `Covers R10.`
  - Un filtre carburant modifié persiste après redémarrage simulé du store.
  - État initial vide : aucun favori, filtres aux valeurs par défaut.
- **Verification** — Tests DataStore verts ; favoris/filtres survivent à un kill/restart de l'app.

### Phase 3 — Écrans (parité 7 écrans, repris des maquettes Stitch)

### U6. Écrans data : Liste + Détail station

- **Goal** — Les deux écrans cœur : liste triable et détail station avec tendance.
- **Requirements** — R5, R6, R7, R8, R10.
- **Dependencies** — U4, U5.
- **Files** (nouveaux) — `.../ui/stations/{StationsScreen,StationsViewModel}.kt`,
  `.../ui/stations/StationCard.kt`, `.../ui/detail/{StationDetailScreen,DetailViewModel}.kt`,
  `.../ui/detail/PriceTrendBars.kt`, `.../ui/common/FuelChip.kt`,
  `.../domain/{PriceTier,Distance}.kt` (KTD6).
- **Approach** — Liste : cards marque + adresse + distance + chips carburant + badge « Le moins
  cher », tri prix/distance, barre de recherche d'adresse (U4). Détail : prix par carburant,
  tendance 7 j + bar chart, toggle favori, bouton Itinéraire (deeplink `google.navigation:` /
  geo URI). `PriceTier` (vert→rouge) mirroir de `apps/web/src/lib/priceColor.ts`. Maquettes :
  `stitch_.../liste_des_stations/` et `.../d_tails_de_la_station/`.
- **Test scenarios**
  - Tri par prix : la station la moins chère apparaît en tête avec le badge « Le moins cher ».
  - Tri par distance : ordonné par distance croissante depuis la position courante.
  - Tier de prix : un prix dans le tier bas rend en vert, le tier haut en rouge (bornes = mirroir
    `priceColor.ts`). `Covers AE3.`
  - Détail : la tendance 7 j affiche une flèche hausse/baisse cohérente avec la série ; série vide
    → pas de crash, état « pas de données ».
  - Favori : le toggle depuis le détail persiste (U5).
  - Deeplink : le bouton Itinéraire ouvre une intent geo valide (vérif intent, pas navigation
    réelle).
- **Verification** — Écrans navigables avec data réelle ; tri, tiers, favori, deeplink OK.

### U7. Écrans secondaires : Favoris, Tendances, Réglages

- **Goal** — Compléter la parité 7 écrans.
- **Requirements** — R5, R6, R9, R10.
- **Dependencies** — U4, U5, U6.
- **Files** (nouveaux) — `.../ui/favorites/FavoritesScreen.kt`,
  `.../ui/trends/{TrendsScreen,TrendsViewModel}.kt`, `.../ui/trends/TrendChart.kt`,
  `.../ui/settings/SettingsScreen.kt`.
- **Approach** — Favoris : réutilise `StationCard` (U6) filtrée sur les ids favoris (U5), état
  vide illustré. Tendances : multi-courbes moyennes nationales, toggle 30 j / 90 j / 1 an, depuis
  `history.json` + `history-countries.json` (U4) ; graphe Compose (Canvas ou lib légère).
  Réglages : écran de démarrage, unité, avertissement données anciennes, crédits sources, lien
  politique de confidentialité (U9). Maquettes `favoris/`, `tendances/`, `r_glages/`.
- **Patterns to follow** — `apps/web/src/screens/{FavoritesScreen,TrendsScreen,SettingsScreen}.tsx`.
- **Test scenarios**
  - Favoris vide : état vide affiché ; après ajout, la station apparaît. `Covers R10.`
  - Tendances : le toggle 30/90/365 change la fenêtre de la série ; série manquante → message,
    pas de crash.
  - Réglages : changer l'écran de démarrage modifie l'onglet initial au prochain lancement (U5).
- **Verification** — 3 écrans fonctionnels et cohérents avec les maquettes.

### U8. Écran Carte natif (Google Maps Compose, clustering, pins) — 🔴 gated clé Maps

- **Goal** — Carte avec stations clusterisées, pins par tier, sheet de détail rapide.
- **Requirements** — R5, R6, R11, R12.
- **Dependencies** — U4, U6 (composants prix/tier), clé Google Maps (bloquant externe).
- **Files** (nouveaux) — `.../ui/map/{MapScreen,MapViewModel}.kt`,
  `.../ui/map/StationClusterItem.kt`, `.../ui/map/StationSheet.kt` ;
  `apps/android/app/src/main/AndroidManifest.xml` (clé Maps via placeholder + permission
  localisation précise foreground).
- **Approach** — `maps-compose` 8.3.1 + `maps-compose-utils` `Clustering` (KTD4). Pins colorés
  par tier (U6). Chargement des stations du viewport via `DeptIndex` (U4) au déplacement de
  caméra. Recherche d'adresse (U4) recentre la caméra. Bottom sheet de détail rapide au tap d'un
  pin → lien vers Détail (U6). Localisation **précise foreground only** (R12, KTD5). Clé lue depuis
  `BuildConfig.MAPS_API_KEY`, injectée au manifest via `manifestPlaceholders`, jamais committée.
  Maquette `carte_accueil/`.
- **Execution note** — Développable avec une clé de dev locale (`local.properties`) ; **vérification
  de bout en bout gated** tant que la clé prod (billing) n'existe pas. Sans clé, l'app doit dégrader
  proprement (message « carte indisponible »), pas crasher — les autres écrans restent utilisables
  (AE4).
- **Test scenarios**
  - Clustering : N stations dans le viewport se regroupent en clusters ; le zoom éclate les
    clusters en pins individuels. `Covers R11.`
  - Tier : les pins reprennent la couleur de tier de U6.
  - Tap pin → sheet avec nom/marque/prix + accès au Détail.
  - Recherche d'adresse recentre la caméra sur la zone.
  - Absence de clé : l'écran affiche un fallback, sans crash. `Covers AE4.`
  - Permission localisation refusée : la carte reste utilisable centrée sur une position par
    défaut, sans crash.
- **Verification** — Avec clé de dev, carte + clustering + pins fonctionnels sur émulateur.

### Phase 4 — Distribution Play Store

### U9. Préparation release & publication Play Store — 🔴 gated clé Maps + compte dev

- **Goal** — Rendre l'app publiable : signature, conformité data, fiche.
- **Requirements** — R13, R12.
- **Dependencies** — U1–U8 (app complète), clé Google Maps prod, compte développeur Google Play.
- **Files** — `apps/android/app/build.gradle.kts` (signingConfigs release, `minifyEnabled`,
  R8/ProGuard), `apps/android/keystore.properties` (git-ignoré),
  `apps/web/public/privacy/` ou page dédiée (politique de confidentialité hébergée), assets fiche
  store (icône adaptive depuis le logo Stitch, captures des 7 écrans).
- **Approach** — Build release signé (keystore hors dépôt). **Politique de confidentialité**
  obligatoire (localisation précise, absence de collecte serveur — l'app ne fait que consommer des
  JSON publics) hébergée à une URL stable (ex. sous GH Pages). **Formulaire Data Safety** : déclarer
  catégorie *Location* (précise, foreground), cohérent avec la politique et le comportement réel
  (les incohérences sont un motif de rejet fréquent). Restreindre la clé Maps par package name +
  SHA-1 de la clé de signing (R12). Icône adaptive + fiche store depuis les maquettes.
- **Execution note** — Étape finale ; démarrable seulement une fois la clé Maps prod obtenue et le
  compte dev Google Play créé. Peut être préparée (documents, assets, config gradle) avant, la
  soumission effective venant en dernier.
- **Test scenarios**
  - Build : `./gradlew :app:bundleRelease` produit un AAB signé.
  - Cohérence : la politique de confidentialité mentionne la localisation précise déclarée au
    Data Safety. `Covers R13.`
  - Clé restreinte : la clé Maps prod refuse une origine autre que le package/SHA-1 de l'app.
    `Covers R12.`
- **Verification** — AAB signé généré ; checklist Data Safety + politique alignées ; clé restreinte
  validée.

---

## Verification Contract

- **CI web** : `deploy.yml` et `update-data.yml` verts après U2 ; URL publiée et URLs data
  inchangées (`curl` 200 sur `.../fuel-radar/data/meta.json`). (AE1, R3, R4)
- **Build Android** : `./gradlew :app:assembleDebug` vert après chaque unit de phase 2-3.
- **Tests unitaires Android** : parsing data, sélection département, cache offline, persistance
  (U4, U5) verts.
- **Parité écrans** : les 7 écrans navigables reprennent les maquettes Stitch (AE3).
- **Data non embarquée** : l'APK debug ne contient aucun JSON de `public/data` (AE2, R7).
- **Offline** : liste d'un département déjà consulté visible hors-ligne (AE5, R9).
- **Carte** : avec clé de dev, clustering + pins fonctionnels ; sans clé, fallback propre (AE4).

## Definition of Done

- [ ] Monorepo en place (`apps/web`, `apps/android`), historique git préservé (U1).
- [ ] Déploiement GH Pages et cron data verts, URLs de data strictement inchangées (U2, AE1, R4).
- [ ] Web fonctionnellement identique à avant le déplacement (R2).
- [ ] App Android : 7 écrans en parité, data fetchée à distance, offline de base, favoris/filtres
      persistés (U3–U8 ; R5–R11).
- [ ] Aucune data embarquée dans l'APK (AE2, R7).
- [ ] Carte : fonctionnelle avec clé de dev ; fallback sans clé (U8, AE4). *(Vérif prod gated clé.)*
- [ ] Release préparée : AAB signé, politique de confidentialité + Data Safety alignées, clé Maps
      restreinte (U9, R12, R13). *(Soumission gated clé prod + compte dev.)*

---

## Risks & Dependencies

- **Déploiement cassé pendant la restructuration** — U1 et U2 doivent atterrir ensemble ;
  validation via `workflow_dispatch` sur branche avant tout push sur `main` (main auto-déploie).
- **Dérive des chemins racine** dans les workflows (le `path:` de `upload-pages-artifact` et les
  `git add` ne suivent PAS `working-directory`) — traité explicitement en U2/KTD7.
- **Clé Google Maps (billing)** — bloque U8 (vérif prod) et U9 (soumission). Mitigation : clé de
  dev locale pour développer U8 ; fallback sans clé pour ne pas bloquer le reste.
- **Divergence data web/native** — la native réimplémente la logique (KTD6) ; risque de dérive si
  les formats data évoluent côté web. Mitigation : modèles Kotlin mirroir stricts de
  `apps/web/src/types/index.ts`, à re-synchroniser si le pipeline data change.
- **Conformité Play** — incohérence politique ↔ Data Safety = motif de rejet ; traité en U9.

---

## Sources & Research

- Web data layer : `apps/web/src/lib/data.ts`, `vite.config.ts` (runtimeCaching), workflows
  `deploy.yml` / `update-data.yml` (chemins racine à adapter).
- Brainstorm origine : ce document (section Product Contract), décisions du 2026-07-22.
- Maquettes Stitch : `stitch_fuelradar_mobile_app_design (1).zip` (7 écrans HTML/Tailwind + `DESIGN.md`).
- Google Maps Compose 8.3.1 — [GitHub](https://github.com/googlemaps/android-maps-compose),
  [doc officielle](https://developers.google.com/maps/documentation/android-sdk/maps-compose) (maj 2026-07-10).
- Play Store — [Data Safety](https://support.google.com/googleplay/android-developer/answer/10787469),
  [maj politique localisation 15/04/2026](https://support.google.com/googleplay/android-developer/answer/16926792).

---

## How This Work Fits Together

Une unité de travail cohérente : monorepo + app native v1. Sujets adjacents hors périmètre mais
liés : la **migration MapTiler du web** (déjà partiellement câblée côté web via `VITE_MAPTILER_KEY`)
reste indépendante — la native utilise Google Maps, découplé des tuiles web. Une éventuelle
**factorisation de logique métier** web/native n'est pas justifiée tant qu'il n'y a qu'une
plateforme native (KTD6).
