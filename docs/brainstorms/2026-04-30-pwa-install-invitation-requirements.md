---
date: 2026-04-30
topic: pwa-install-invitation
---

# Invitation à installer la PWA dans le welcome overlay

## Problem Frame

L'application est déjà packagée comme PWA (manifest, icônes, meta tags) mais aucune invitation n'incite à l'installer. Les utilisateurs récurrents passent par le navigateur à chaque visite alors que l'app installée offrirait un accès en un tap, plein écran et la séparation des données du contexte navigateur. Aujourd'hui :

- Aucun service worker n'est enregistré → Chrome/Edge/Android ne déclenchent jamais `beforeinstallprompt`, donc même les utilisateurs prêts à installer n'ont pas de bouton d'install discoverable
- Aucune logique ne détecte l'état d'installation côté app
- Le welcome overlay (`src/App.tsx` ~ligne 270, affiché tant qu'aucune ville n'est sélectionnée) est le moment idéal pour un appel discret : l'utilisateur est engagé mais pas encore en plein workflow

## User Flow

```
                ┌────────────────────────────┐
                │  L'app se charge            │
                └────────────┬────────────────┘
                             │
                ┌────────────┴────────────┐
                │ Détection au mount       │
                │ - display-mode standalone│
                │ - localStorage flag       │
                │ - getInstalledRelatedApps │
                └────────────┬─────────────┘
                             │
                  ┌──────────┴──────────┐
                  │                     │
            Installé /              Navigateur,
            standalone              non installé
                  │                     │
                  ▼                     ▼
          ┌──────────────┐    ┌──────────────────┐
          │ Pas d'invite │    │ Welcome overlay  │
          └──────────────┘    │ + lien « ⤓       │
                              │ Installer »       │
                              └────────┬──────────┘
                                       │ clic
                                       ▼
                          ┌────────────────────────┐
                          │ BeforeInstallPrompt     │
                          │ capturé en mémoire ?    │
                          └────┬──────────────┬────┘
                               │              │
                           Oui │           Non│ (iOS, Firefox,
                               │              │  prompt consommé,
                               ▼              │  in-app browser)
                       ┌────────────────┐    ▼
                       │ prompt() natif  │  ┌─────────────────────┐
                       └─┬───────────┬──┘  │ Mini-modal           │
                  accept │           │     │ instructions FR      │
                         │   dismiss │     │ (Safari iOS) ou       │
                         ▼           ▼     │ « Ouvrez dans Safari » │
                ┌──────────────┐ ┌────────┘ └──────────┬───────────┘
                │ appinstalled │ │ Lien reste visible,             │
                │ + flag local │ │ prochain clic → mini-modal      │
                └──────┬───────┘ └─────────────────────────────────┘
                       │
                       ▼
                ┌──────────────────────────┐
                │ Invite cachée immédiat.  │
                │ + cachée toutes sessions │
                │ futures (flag localStor.) │
                └──────────────────────────┘
```

## Requirements

**Service worker (prérequis technique)**

- R1. Enregistrer un service worker minimal via `vite-plugin-pwa` pour satisfaire le critère d'installabilité Chromium (`beforeinstallprompt`). Précache de l'app shell uniquement : `index.html`, bundles JS/CSS générés par Vite, icônes (`pwa-*.png`, `apple-touch-icon-*.png`, `favicon.svg`/`.ico`), `manifest.webmanifest`. **Exclusions explicites** (NetworkOnly ou non-interceptés) : `/data/**` (JSON stations + meta), tuiles map externes (CARTO/MapTiler — autres origines, pas affectées de toute façon)
- R1b. Configuration `vite-plugin-pwa` à respecter pour le sous-chemin GitHub Pages : `base: '/carburants-france/'`, `scope: '/carburants-france/'`, registration via `import { registerSW } from 'virtual:pwa-register'` (qui respecte `import.meta.env.BASE_URL`) — **pas** de `navigator.serviceWorker.register('/sw.js')` en dur (404 garanti)
- R2. Stratégie de mise à jour : `registerType: 'autoUpdate'` + `NetworkFirst` sur les requêtes de navigation (`index.html`). Conséquences : (i) les JSON dans `/data/**` ne sont pas interceptés (NetworkOnly explicite ou exclusion via `globIgnores`), donc fraîcheur 2h préservée ; (ii) au reload de l'app, l'utilisateur récupère toujours le dernier `index.html` (chargeant les hash bundles à jour), avec fallback précache si offline ; (iii) le SW lui-même doit être servi avec `Cache-Control: no-cache` pour que le CDN GitHub Pages ne bloque pas la détection de la nouvelle version. *Conséquence utilisateur* : nouvelle version visible au prochain reload de l'app installée (pas besoin d'attendre un cold start complet)

**Détection d'état**

- R3. Détecter "lancé en standalone" via `window.matchMedia('(display-mode: standalone)').matches` OU `(navigator as any).standalone === true` (iOS) → masquer toute invite d'installation
- R4. ~~Détection « déjà installé en onglet navigateur » via `navigator.getInstalledRelatedApps()`~~ — **retiré**. L'API requiert un `related_applications` (Play Store / TWA) qui n'existe pas pour ce projet, donc elle ne matcherait jamais. Le rôle « masquer l'invite quand installé hors standalone » est porté par le flag localStorage de R5 (posé sur `appinstalled` ou `prompt() accepted`)
- R5. Capturer l'événement `beforeinstallprompt` au démarrage et stocker la promesse pour usage différé. Capturer `appinstalled` pour masquer l'invite immédiatement après installation. **Persistance cross-session** : poser un flag `localStorage.pwa_installed = '1'` quand `appinstalled` se déclenche OU quand `prompt()` retourne `outcome === 'accepted'` ; relire ce flag au mount pour masquer l'invite sur les sessions ultérieures, même si l'utilisateur revient en onglet navigateur (compense l'indisponibilité de R4 pour ce projet)

**Invitation dans le welcome overlay**

- R6. Ajouter un lien discret « ⤓ Installer l'application » dans le welcome overlay (`src/App.tsx`), positionné **après** la ligne « ou tapez une ville ci-dessus », visuellement séparé par un divider léger pour ne pas concurrencer le CTA primaire « Me localiser »
- R7. Le lien n'apparaît jamais dans les cas suivants : (a) standalone détecté (R3), (b) flag `pwa_installed` présent dans localStorage (R5), (c) la mini-modal d'instructions est ouverte. Côté re-prompt : après `prompt()` qui retourne `dismissed`, le lien reste visible mais pour le **reste de la session courante** un nouveau clic doit fallback sur la mini-modal d'instructions (la `BeforeInstallPromptEvent` capturée n'est utilisable qu'une fois)
- R8. Branchement par capacité, pas par OS, à l'instant du clic :
   1. Si `BeforeInstallPromptEvent` est en mémoire (non consommé) → appeler `prompt()` ; lire `userChoice` ; si `accepted` → poser le flag (R5) ; si `dismissed` → consommer la référence et basculer R8→R9 pour les clics suivants
   2. Sinon (iOS Safari, Firefox desktop, Chromium avec prompt déjà consommé/supprimé par heuristique d'engagement) → ouvrir la mini-modal d'instructions (R9)
- R9. Mini-modal d'instructions installable sans framework : titre « Installer l'application », deux étapes courtes en français adaptées à Safari iOS (« Appuyez sur le bouton Partager <icône> puis 'Sur l'écran d'accueil' »), icône Share inline (SVG, pas screenshot pour vieillir mieux). Garde-fou : si le navigateur courant est un in-app WebView (Instagram, LinkedIn, Gmail) où Add-to-Home-Screen n'existe pas, afficher à la place « Ouvrez ce site dans Safari pour l'installer »
- R9b. Comportement minimal de la mini-modal : bouton **Fermer** explicite, fermeture aussi sur clic du scrim et touche `Escape` ; `role="dialog"` + `aria-modal="true"` + `aria-labelledby` pointant sur le titre ; focus déplacé sur le titre à l'ouverture, restitué sur le lien d'invite à la fermeture ; sizing : `max-w-sm` centré ≥640px, full-width sheet collé au bas <640px (cohérent avec le bottom-sheet mobile existant). Animation et illustration exactes restent en deferred design

**Persistance et lifecycle**

- R10. Aucun bouton de fermeture sur l'invitation : elle reste affichée à chaque visite (dans le welcome overlay, donc seulement avant sélection d'une ville) tant que les conditions de masquage de R7 ne sont pas vraies
- R11. La transition d'état doit être réactive : si `appinstalled` se déclenche pendant la session courante, l'invite et la modal doivent disparaître sans rechargement

## Success Criteria

- Sur Chrome desktop / Edge / Chrome Android avec `beforeinstallprompt` exposé, un utilisateur non installé voit le lien et peut installer l'app en 2 clics depuis le welcome overlay (lien → bouton "Installer" du prompt natif)
- Sur iOS Safari, l'utilisateur voit le lien et obtient des instructions claires en français pour ajouter l'app à l'écran d'accueil
- Après installation, ni la session courante ni les sessions suivantes (sur le même profil navigateur) ne montrent l'invitation, que l'app soit ouverte en standalone ou en onglet navigateur. *Limitation acceptée* : un utilisateur qui a installé sur un autre profil ou un autre device verra l'invite jusqu'à un nouvel `appinstalled` ou à passer en standalone
- Le critère Chromium d'installabilité (manifest valide + SW actif) est rempli, vérifiable via Chrome DevTools → Application → Manifest
- *Pas de critère quantitatif de conversion* : le projet n'instrumente pas l'install (cf. Scope Boundaries). La validation post-merge est qualitative : tester chaque branche manuellement (Chrome desktop, Chrome Android, Safari iOS, in-app WebView) sur la regression checklist du PR

## Scope Boundaries

- **Pas** de mode hors-ligne : ni cache des tuiles map, ni cache des JSON de stations. Le SW n'est là que pour débloquer l'installabilité
- **Pas** de banner persistant ni de toast post-installation. Pas d'A/B test, pas d'analytics d'install
- **Pas** d'invitation ailleurs que dans le welcome overlay (pas dans le footer, pas dans la sidebar, pas après sélection d'une ville)
- **Pas** de logique de re-prompt après dismiss : il n'y a pas de bouton de fermeture
- **Pas** de changements de wording/visuels au-delà du nouveau lien et de la mini-modal iOS

## Key Decisions

- **Approche hybride SW + fallback iOS** : couvre Android/Chromium proprement et n'exclut pas iOS. *Rationale* : sans SW Chromium n'expose pas du tout l'invite native ; iOS Safari n'expose jamais `beforeinstallprompt` et requiert toujours un fallback manuel. La perte de feature sans SW (~majorité du parc Android/desktop) ne justifie pas l'économie d'une dépendance
- **Stratégie d'update `autoUpdate` + `NetworkFirst` sur `index.html`** : nouvelle version visible au prochain reload de l'app, JSON jamais cachés (fraîcheur 2h préservée), pas de toast (cohérent avec Scope Boundaries)
- **Placement dans le welcome overlay uniquement** : le moment où l'utilisateur n'est pas encore en flux de recherche est le moins intrusif. Une fois une ville sélectionnée, l'overlay disparaît et l'invite avec. *Limite acceptée* : un utilisateur récurrent dont la ville est restaurée ne reverra pas l'invite — voir Outstanding Questions
- **Persistance sans bouton X** : l'invite est un lien discret, pas un banner, donc pas besoin de mécanisme de dismiss. Elle disparaît à l'installation (immédiate via `appinstalled`/flag, persistante cross-session) ou en standalone
- **R4 retiré** : `getInstalledRelatedApps()` requiert un store, hors-scope. Le flag localStorage de R5 porte la persistance cross-session du « déjà installé »
- **SW minimal pour installabilité** : éviter de sur-engineerer le offline alors que les données changent toutes les 2h. Précache de l'app shell uniquement

## Dependencies / Assumptions

- Ajout de la dépendance `vite-plugin-pwa` (et son peer `workbox-window` selon config). Compatibilité Vite 7 + React 19 à valider en planning (vérifier le changelog du plugin)
- Source de vérité du manifest : conserver `public/manifest.webmanifest` ; configurer `vite-plugin-pwa` avec `manifest: false` pour qu'il n'en génère pas un second. Le `<link rel="manifest" href="/carburants-france/manifest.webmanifest">` actuel d'`index.html` reste tel quel
- Le déploiement GitHub Pages sert sur HTTPS (déjà le cas) — prérequis pour service worker
- Hypothèse : aucun utilisateur n'a aujourd'hui un SW orphelin enregistré sur ce domaine (le projet n'en a jamais expédié) → pas de migration de SW à gérer

## Outstanding Questions

### Resolve Before Planning

_(aucune — les 4 décisions structurantes ont été tranchées : SW in-scope, update strategy = autoUpdate+NetworkFirst, R4 retiré, manifest source = public/manifest.webmanifest)_

### Deferred to Planning

- [Affects R6][Design] Wording exact ("Installer l'application", "Ajouter à l'écran d'accueil", "Installer Carburants"), microcopy de bénéfice utilisateur courte (ex. « Lancement instantané depuis l'écran d'accueil »), et choix d'icône Lucide (`Download`, `Smartphone`, `MonitorDown`)
- [Affects R6][Design] Hiérarchie visuelle exacte du lien dans l'overlay : taille/poids/couleur **identiques ou plus discrets** que le lien existant « ou tapez une ville ci-dessus » ; nécessité réelle du « divider léger » (R6) à challenger — préférer du whitespace pur si le test utilisateur ne valide pas le divider
- [Affects R6][A11y] Élément HTML : `<button type="button">` (action avec side-effects), hit area ≥44×44px, `aria-haspopup="dialog"` actif uniquement quand le runtime branche vers la mini-modal (R9), `focus-visible` aligné sur les autres boutons de l'overlay
- [Affects R6, R10][UX] Sizing de l'overlay sur viewports <700px de haut (iPhone SE) après ajout du 3e élément ; vérifier qu'il ne chevauche pas le bottom-sheet des stations / la barre de filtres carburants existante
- [Affects R9][Design] Maquette finale de la mini-modal iOS : illustration Share inline (SVG, pas screenshot), animation, padding mobile vs desktop. Le squelette comportemental est figé en R9b, seule l'apparence reste ouverte
- [Affects R6][Product] Le welcome overlay disparaît dès qu'une ville est sélectionnée et n'est jamais re-montré dans la session courante. Conséquence : un utilisateur récurrent dont la dernière ville est restaurée au mount ne verra **jamais** l'invite. Décider en planning : (i) accepter que la cible v1 est uniquement les premiers visiteurs, ou (ii) ajouter un point d'entrée secondaire (bouton « Installer » dans le footer existant, déjà en lecture pour `lastUpdate`)
- [Affects R1][Reliability] Plan de rollback si le premier déploiement SW est mal configuré pour le scope `/carburants-france/` : prévoir un script de désenregistrement (ex. `navigator.serviceWorker.getRegistrations().then(rs => rs.forEach(r => r.unregister()))`) déployable rapidement

## Next Steps

→ `/ce:plan` for structured implementation planning
