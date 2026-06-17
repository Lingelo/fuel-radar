---
date: 2026-06-17
topic: ideation-tech-functional
type: ideation
---

# Idéation — Sujets d'amélioration tech & fonctionnels

Phase d'idéation complète sur **Carburants France** (PWA React 19 / TS / Vite,
données prix-carburants.gouv.fr). Objectif : produire un catalogue priorisé de
sujets, ancré dans l'état réel du code, pour alimenter ensuite des brainstorms
ciblés (`/ce:plan`) sur les sujets retenus.

## État des lieux (résumé)

- **~4 700 LOC, 39 fichiers**, TS strict, ESLint, zéro `TODO/FIXME`. Base saine.
- **State** : 4 React Context (`Filters`, `View`, `Favorites`, `Settings`),
  persistance localStorage. Pas de lib externe.
- **Routing** : hash maison (`ViewContext`), deep-link + partage de zone `?lat&lng&fuel…`.
- **Données** : JSON statiques `public/data/**` générés par 2 scripts `.mjs`,
  servis via SW (NetworkFirst data, CacheFirst tuiles). Rafraîchis toutes les 2 h (cron).
- **PWA** : manifest, SW autoUpdate, install prompt multi-plateforme, offline runtime.
- **Écrans** : Carte, Stations, Détail, Favoris, Tendances, Réglages.

### Points faibles structurants (constats)

1. **Zéro test** — aucun runner, aucune couverture, même sur la logique pure
   critique (`department`, `distance`, `priceColor`, `shareUrl`, `geocode`).
2. **Pas de gate CI sur PR** — `deploy.yml` ne tourne que sur `push main` ;
   lint/typecheck/tests ne valident jamais une PR avant merge.
3. **`MapScreen.tsx` = 840 LOC** — seul hotspot de complexité, mélange carte,
   pins, cercle de rayon, recentrage, overlay de recherche, panneau latéral.
4. **Pipeline historique en `--bootstrap` toutes les 2 h** — re-télécharge et
   recalcule l'année entière à chaque run, alors qu'un mode `--daily`
   incrémental existe déjà (`README`) mais n'est pas branché en CI.
5. **`deploy.yml` régénère toutes les données à chaque push `main`** — même un
   changement purement front/doc déclenche `process-data` + `--bootstrap`.
6. **197 fichiers `public/data/**` committés** alors que la CI les régénère —
   snapshots périmés qui gonflent l'historique git.
7. **`react-leaflet-markercluster@5.0.0-rc.0`** — dépendance en *release
   candidate* en production.
8. **Scripts `.mjs` non typés** — types `Station`/`FuelPrice` dupliqués
   implicitement entre pipeline et app, aucun garde-fou si le schéma dérive.
9. **Pas d'error boundary ni d'observabilité** — `data.ts` avale les erreurs
   silencieusement (`catch {}` → `[]`), aucune visibilité sur les échecs réels.

---

## Axe FONCTIONNEL (orienté utilisateur)

| # | Idée | Problème résolu | Valeur | Effort |
|---|------|-----------------|--------|--------|
| F1 | **Filtre par services** (lavage, GPLc, boutique, air, toilettes) | Les `services[]` + `SERVICE_ICONS` existent déjà mais ne sont pas filtrables | Moyenne | **Faible** |
| F2 | **Mode sombre** | Tokens Material 3 déjà en place, dark non câblé ; confort + conduite de nuit | Élevée | Moyen |
| F3 | **Alertes de prix** (seuil sur carburant favori, push PWA) | Aucun rappel ; l'utilisateur doit ré-ouvrir l'app manuellement | **Élevée** | Élevé |
| F4 | **Stations sur un trajet** (A→B, pas seulement autour d'un point) | Le cas d'usage « plein sur la route » n'est pas couvert | **Élevée** | Élevé |
| F5 | **Économie estimée par plein** (€ vs moyenne locale × capacité réservoir) | Le prix brut ne parle pas ; chiffrer le gain réel motive | Élevée | Moyen |
| F6 | **Profil véhicule** (carburant par défaut + capacité réservoir) | Re-sélection manuelle à chaque session ; alimente F5 | Moyenne | Faible |
| F7 | **Comparaison multi-carburants** | Un seul carburant sélectionnable à la fois | Moyenne | Moyen |
| F8 | **Historique 30/90 j sur le détail station** | Le national 30/90/365 existe (Tendances), mais le détail station se limite à 7 j | Moyenne | Moyen |
| F9 | **Indicateur « vaut-il mieux attendre ? »** (tendance hausse/baisse) | Aucune aide à la décision temporelle | Moyenne | Moyen |
| F10 | **Partage station via Web Share API native** + carte image | Partage actuel limité à la zone ; pas de partage station riche | Faible | Faible |
| F11 | **Classement / comparaison départementale** sur Tendances | Pas de mise en perspective géographique | Faible | Moyen |
| F12 | **i18n (EN)** pour les touristes | UI 100 % FR codée en dur | Faible | Moyen |
| F13 | **Signalement collaboratif** (prix erroné / station fermée) | Aucun retour terrain ; nécessite un backend (rupture d'archi) | Moyenne | **Élevé** |

## Axe TECHNIQUE (qualité, archi, perf, ops)

| # | Idée | Problème résolu | Valeur | Effort |
|---|------|-----------------|--------|--------|
| T1 | **Suite de tests** (Vitest + Testing Library), d'abord la logique pure | Constat #1 : zéro filet de sécurité | **Élevée** | Moyen |
| T2 | **Gate CI sur PR** (lint + `tsc` + tests) | Constat #2 : rien ne valide une PR avant merge | **Élevée** | **Faible** |
| T3 | **Historique CI en `--daily` incrémental** (+ persistance du dernier `history.json`) | Constat #4 : re-bootstrap de l'année toutes les 2 h | Élevée | Moyen |
| T4 | **Découpler refresh données ↔ déploiement code** | Constat #5 : push code = re-télécharge tout | Moyenne | Moyen |
| T5 | **Sortir `public/data/**` du tracking git** (ou snapshot minimal) | Constat #6 : 197 fichiers générés versionnés | Moyenne | Faible |
| T6 | **Refactor `MapScreen`** → sous-composants/hooks (`usePricePins`, `RadiusCircle`, `MapRecenter`, `SearchOverlay`) | Constat #3 : 840 LOC fragiles | Élevée | Moyen |
| T7 | **Types partagés pipeline ↔ app** (passer les scripts en TS ou importer les types) | Constat #8 : schéma dupliqué, dérive silencieuse | Moyenne | Moyen |
| T8 | **Code splitting / lazy load** (charts, écran Détail, Trends ; alléger le bundle Leaflet) | Bundle initial chargé d'un bloc | Moyenne | Faible |
| T9 | **Error boundary + observabilité légère** (ex. log d'échec data, indicateur réseau) | Constat #9 : erreurs avalées silencieusement | Moyenne | Faible |
| T10 | **Pin de `react-leaflet-markercluster`** (sortir du RC) | Constat #7 : dépendance RC en prod | Faible | Faible |
| T11 | **Lighthouse CI / audit a11y automatisé** (axe) | A11y manuelle non régressée automatiquement | Moyenne | Faible |
| T12 | **Indicateur hors-ligne + état de fraîcheur** dans l'UI | `isStale()` existe mais pas d'état offline explicite | Faible | Faible |
| T13 | **E2E smoke (Playwright)** sur parcours critiques (localiser → liste → détail → partage) | Aucune validation bout-en-bout | Moyenne | Moyen |

---

## Matrice de priorisation

```
              EFFORT FAIBLE                 EFFORT ÉLEVÉ
           ┌───────────────────────┬───────────────────────┐
  IMPACT   │  QUICK WINS           │  BIG BETS              │
  ÉLEVÉ    │  T2  Gate CI PR       │  T1  Tests (socle)     │
           │  F1  Filtre services  │  F3  Alertes prix push │
           │  T10 Pin dépendance   │  F4  Stations trajet   │
           │  T8  Code splitting   │  F2  Mode sombre*      │
           │  T11 Lighthouse/axe   │  T3  Historique daily  │
           ├───────────────────────┼───────────────────────┤
  IMPACT   │  F10 Web Share        │  T6  Refactor MapScreen│
  MOYEN    │  F6  Profil véhicule  │  F5  Économie estimée  │
           │  T9  Error boundary   │  T4  Découpler data    │
           │  T12 Indicateur offline│ F7  Multi-carburants  │
           │  T5  Untrack data     │  F8  Histo détail 30/90│
           └───────────────────────┴───────────────────────┘
  * F2/T1/T3 sont moyens en effort mais à fort levier → candidats prioritaires.
```

## Recommandation de séquencement

**Vague 1 — Fondations (dérisque tout le reste)**
- **T2** Gate CI sur PR (lint + typecheck) — la plus rentable, quasi immédiate.
- **T1** Socle de tests sur la logique pure (`distance`, `priceColor`,
  `department`, `shareUrl`, `geocode`), branché dans T2.
- **T10** Sortir `react-leaflet-markercluster` du RC.

**Vague 2 — Valeur utilisateur rapide**
- **F1** Filtre par services (réutilise l'existant).
- **F2** Mode sombre (tokens déjà là).
- **T8** Code splitting / lazy charts.

**Vague 3 — Efficacité ops & dette**
- **T3** Historique `--daily` en CI + **T4** découplage data/déploiement + **T5** untrack data (lot cohérent).
- **T6** Refactor `MapScreen`.

**Vague 4 — Gros paris produit**
- **F3** Alertes de prix (push) ou **F4** stations sur trajet — à instruire en
  brainstorm dédié (notamment l'impact archi : F3/F13 demandent potentiellement
  un backend, ce qui rompt le modèle 100 % statique GitHub Pages).

## Questions ouvertes à trancher

- **Backend ou pas ?** F3 (push), F4 (routing/trajet via API externe), F13
  (signalement) sortent du modèle statique actuel. Décider si on reste « full
  static GitHub Pages » ou si on s'autorise un service léger (Cloudflare
  Worker, fonction serverless).
- **Périmètre offline** : jusqu'où pousser (T12) sachant que les données changent toutes les 2 h ?
- **i18n (F12)** : y a-t-il une cible non francophone réelle, ou priorité basse ?

## Prochaines étapes

→ Choisir 2–3 sujets de la Vague 1/2 et lancer `/ce:plan` pour un brainstorm de
requirements détaillé par sujet (format `docs/brainstorms/*-requirements.md`).
