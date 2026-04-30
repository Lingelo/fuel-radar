---
date: 2026-04-30
topic: pwa-logo-mobile-fuel-filter
---

# Nouvelle icône PWA + filtre carburants mobile

## Problem Frame

L'icône PWA actuelle (goutte de carburant abstraite) ne communique pas clairement "station-essence" aux utilisateurs. Par ailleurs, sur mobile les 6 boutons de filtre carburant (Gazole, E10, SP98, SP95, E85, GPLc) débordent de leur conteneur sans indication visuelle de scroll.

## Requirements

**Icône PWA**
- R1. Remplacer le SVG source (`public/pwa-icon-source.svg`) par une silhouette de pompe à essence blanche sur fond noir (#171717) avec accent bleu pétrole (#0c4a6e)
- R2. Régénérer les PNG PWA (64, 192, 512, maskable) via `node scripts/generate-pwa-icons.mjs`. La variante maskable doit respecter la safe-zone (contenu centré dans ~80% du canvas) pour éviter le rognage sur les écrans d'accueil Android
- R3. Mettre à jour le favicon (`public/favicon.svg`) pour correspondre au nouveau design

**Filtre carburants mobile**
- R4. Ajouter un indicateur visuel de scroll horizontal (fade/dégradé sur le bord droit) quand les boutons dépassent sur mobile
- R5. Assurer un scroll horizontal fluide (snap optionnel) sans que les boutons wrappent ou soient tronqués

## Success Criteria

- L'icône PWA installée montre clairement une pompe à essence
- Sur mobile (< 768px), les 6 boutons carburants sont tous accessibles via swipe horizontal, sans wrap ni troncature. Seul un dégradé fade sur le bord droit signale la présence de contenu hors-écran

## Scope Boundaries

- Pas de changement au Header (wordmark) ni au layout desktop
- Pas de refonte de la palette de couleurs
- Pas de nouveau nom ou branding

## Next Steps

→ `/ce:plan` for structured implementation planning
