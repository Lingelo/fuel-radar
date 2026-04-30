---
title: "feat: Icône PWA pompe à essence + scroll horizontal filtre mobile"
type: feat
status: completed
date: 2026-04-30
origin: docs/brainstorms/2026-04-30-pwa-logo-mobile-fuel-filter-requirements.md
---

# feat: Icône PWA pompe à essence + scroll horizontal filtre mobile

## Overview

Deux améliorations visuelles indépendantes : (1) remplacer l'icône PWA abstraite (goutte de carburant) par une silhouette de pompe à essence immédiatement reconnaissable, et (2) ajouter un indicateur de scroll horizontal (fade gradient) sur le filtre carburants mobile pour signaler les boutons hors-écran.

## Problem Frame

L'icône PWA actuelle ne communique pas "station-essence" au premier coup d'oeil. Sur mobile, les 6 boutons carburant débordent sans indication visuelle — l'utilisateur ne sait pas qu'il peut scroller. (see origin: `docs/brainstorms/2026-04-30-pwa-logo-mobile-fuel-filter-requirements.md`)

## Requirements Trace

**Icône PWA**
- R1. Nouvelle silhouette pompe à essence (blanche sur #171717, accent #0c4a6e) dans `public/pwa-icon-source.svg`
- R2. Régénérer PNG PWA avec safe-zone maskable (~80% du canvas, soit ~51px de marge sur un viewBox 512x512)
- R3. Favicon mis à jour (`public/favicon.svg`)

**Filtre carburants mobile**
- R4. Indicateur visuel de scroll (fade droit) sur le filtre mobile
- R5. Scroll horizontal fluide, sans wrap ni troncature

## Scope Boundaries

- Pas de changement au Header (wordmark) ni au layout desktop
- Pas de refonte palette ou branding
- Le favicon.ico legacy n'est pas regénéré (le script ne le gère pas, les navigateurs modernes utilisent favicon.svg)

## Context & Research

### Relevant Code and Patterns

- `scripts/generate-pwa-icons.mjs` — utilise `sharp` pour rasteriser le SVG source vers 5 PNG (64, 192, 512, maskable-512, apple-touch-180). Aucun padding spécial pour maskable : le safe-zone doit être intégré dans le SVG source lui-même
- `public/manifest.webmanifest` — déclare 4 icônes (64, 192, 512, maskable-512)
- `src/components/FuelFilter.tsx` — `ToggleGroup` avec `className="flex gap-1 overflow-x-auto"`, chaque bouton `whitespace-nowrap`
- `src/App.tsx:229` — conteneur mobile : `<div className="glass rounded-xl px-3 py-2 shadow-md md:hidden">`
- `src/index.css:96-100` — `.glass` défini en CSS pur (pas `@utility`). Convention du projet pour les classes utilitaires custom
- Aucun pattern `mask-image` ou scroll-fade existant dans le codebase

## Key Technical Decisions

- **Safe-zone dans le SVG, pas dans le script** : Le script `generate-pwa-icons.mjs` rasterise le même SVG pour tous les formats sans padding différencié. Pour respecter la safe-zone maskable, le design de la pompe sera centré dans ~80% du viewBox 512x512 (~51px de marge de chaque côté). C'est plus simple que de modifier le script pour ajouter du padding conditionnel. Note : ce padding réduit légèrement la taille du motif sur les icônes non-maskable (notamment 64px) — garder la silhouette très simple pour compenser
- **Fade via pseudo-élément `::after`, pas `mask-image`** : `mask-image` appliqué sur un élément avec `backdrop-filter` (`.glass`) couperait le flou sur la zone masquée. Un pseudo-élément `::after` positionné en absolu avec un dégradé `transparent → blanc` et `pointer-events: none` évite ce conflit. Il est appliqué sur le conteneur `.glass` mobile via une classe `.scroll-fade-right` dans `index.css`
- **Pas de JS pour le fade** : Le fade reste statique (toujours visible quand `overflow-x-auto` est actif). Pas de détection dynamique de `scrollLeft` — la complexité n'est pas justifiée pour 6 boutons qui dépassent presque toujours sur mobile

## Open Questions

### Resolved During Planning

- **Le script gère-t-il le maskable différemment ?** Non — même SVG, même rendu. Le padding doit être dans le SVG source

### Deferred to Implementation

- **Forme exacte du SVG pompe** : Le design précis de la silhouette (proportions, position du nozzle, placement de l'accent bleu) sera déterminé lors de l'implémentation

## Implementation Units

- [x] **Unit 1: Nouveau SVG source + favicon**

**Goal:** Créer le nouveau design de pompe à essence en SVG et mettre à jour le favicon

**Requirements:** R1, R3

**Dependencies:** Aucune

**Files:**
- Modify: `public/pwa-icon-source.svg`
- Modify: `public/favicon.svg`

**Approach:**
- Dessiner une silhouette de pompe à essence (corps + nozzle/tuyau) en blanc sur fond noir (#171717) avec un accent bleu pétrole (#0c4a6e) — le cercle bleu existant peut être réutilisé comme détail
- Centrer le motif dans ~80% du viewBox 512x512 (~51px de marge de chaque côté) pour que la variante maskable conserve le contenu visible après rognage circulaire/squircle. Ce padding réduit la zone utile à 64px — garder la silhouette très simple
- Le favicon.svg reprend le même design (le viewBox reste 0 0 512 512)

**Patterns to follow:**
- Structure SVG existante : `<rect>` fond + `<path>` motif + éléments d'accent — garder cette simplicité

**Test expectation:** none — asset visuel statique, vérification manuelle

**Verification:**
- Le SVG s'affiche correctement dans le navigateur
- Le motif de pompe est clairement identifiable en miniature (64px)
- Le contenu principal reste visible dans un cercle centré à 80% du canvas (test maskable)

- [x] **Unit 2: Régénérer les PNG PWA**

**Goal:** Rasteriser les nouvelles icônes PNG à partir du SVG mis à jour

**Requirements:** R2

**Dependencies:** Unit 1

**Files:**
- Regenerate: `public/pwa-64x64.png`
- Regenerate: `public/pwa-192x192.png`
- Regenerate: `public/pwa-512x512.png`
- Regenerate: `public/maskable-icon-512x512.png`
- Regenerate: `public/apple-touch-icon-180x180.png`

**Approach:**
- Exécuter `npm install --no-save sharp && node scripts/generate-pwa-icons.mjs`
- Vérifier visuellement chaque PNG généré, en particulier le maskable à 512px dans un masque circulaire

**Test expectation:** none — sortie d'un script de build, vérification manuelle

**Verification:**
- Les 5 fichiers PNG sont régénérés avec le nouveau design
- L'icône maskable conserve la pompe visible après rognage simulé (cercle inscrit)

- [x] **Unit 3: Fade scroll horizontal sur le filtre mobile**

**Goal:** Ajouter un dégradé de fondu sur le bord droit du filtre carburants mobile pour signaler le scroll

**Requirements:** R4, R5

**Dependencies:** Aucune (indépendant des Units 1-2)

**Files:**
- Modify: `src/index.css`
- Modify: `src/App.tsx`

**Approach:**
- Ajouter une classe `.scroll-fade-right` dans `src/index.css` utilisant un pseudo-élément `::after` : positionné en absolu à droite, dégradé `transparent → rgba(255,255,255,0.85)` (la couleur de fond de `.glass`), `pointer-events: none`, largeur ~1.5rem. Le conteneur doit être `position: relative` pour l'ancrage
- Appliquer cette classe sur le `<div>` conteneur mobile du filtre (ligne ~229 de `App.tsx`) : ajouter `scroll-fade-right` à côté de `glass rounded-xl px-3 py-2 shadow-md md:hidden`
- Le `overflow-x-auto` existant dans `FuelFilter.tsx` assure déjà le scroll — ne pas y toucher
- Ne pas appliquer le fade sur desktop (`md:hidden` sur le conteneur suffit)
- Utiliser 1.5rem (24px) plutôt que 2rem pour le fade — le padding `px-3` (12px) en absorbe la moitié, laissant ~12px de fade sur le contenu, ce qui est subtil sans trop masquer le bouton le plus à droite

**Patterns to follow:**
- Convention `.glass` dans `index.css` : classe CSS utilitaire avec commentaire descriptif

**Test scenarios:**
- Happy path: sur un écran < 768px avec 6 boutons, le bord droit affiche un dégradé de fondu et l'utilisateur peut scroller pour voir tous les boutons
- Edge case: après scroll complet vers la droite, le fade reste visible (comportement statique, pas de JS)
- Integration: le fade ne masque pas le clic/tap sur le dernier bouton visible — `pointer-events: none` sur le pseudo-élément laisse passer les interactions
- Integration: le dégradé du pseudo-élément ne coupe pas le `backdrop-filter` de `.glass` (le pseudo-élément est au-dessus, pas un masque)

**Verification:**
- Sur mobile, les 6 boutons sont accessibles par swipe horizontal
- Un dégradé subtil sur le bord droit signale la présence de contenu hors écran
- Le `backdrop-filter` de `.glass` reste intact sur toute la largeur du conteneur
- Le layout desktop n'est pas affecté

## System-Wide Impact

- **Interaction graph:** Aucun callback, middleware ou observer affecté. Les icônes PWA sont des assets statiques référencés par le manifest. Le FuelFilter est un composant de présentation pur
- **API surface parity:** Le manifest.webmanifest ne change pas de structure (mêmes entrées d'icônes, seuls les fichiers PNG sont remplacés)
- **Unchanged invariants:** Le Header, le layout desktop, la palette de couleurs, et le comportement fonctionnel du FuelFilter (sélection de carburant) restent inchangés

## Risks & Dependencies

| Risk | Mitigation |
|------|------------|
| Le SVG pompe peut être illisible en 64px (compounded par la marge safe-zone de ~10%) | Garder la silhouette très simple et sans détails fins, tester visuellement à 64px avant de finaliser |
| Le fade statique masque partiellement le dernier bouton visible | Fade limité à 1.5rem (24px) dont ~12px sur le padding — le bouton reste cliquable grâce à `pointer-events: none` sur le pseudo-élément |
| Couleur du dégradé fade doit correspondre au fond `.glass` | Utiliser `rgba(255,255,255,0.85)` — la même valeur que `.glass`. Si `.glass` évolue, le fade devra être mis à jour |

## Sources & References

- **Origin document:** [docs/brainstorms/2026-04-30-pwa-logo-mobile-fuel-filter-requirements.md](docs/brainstorms/2026-04-30-pwa-logo-mobile-fuel-filter-requirements.md)
- Related code: `scripts/generate-pwa-icons.mjs`, `src/components/FuelFilter.tsx`, `src/index.css`
