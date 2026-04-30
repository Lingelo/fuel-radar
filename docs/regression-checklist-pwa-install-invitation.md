# Regression Checklist — PWA Install Invitation

Manual validation for the PWA install invitation feature. Attach the completed checklist (with each line marked ✅ / ❌ / N/A) to the pull request before merging.

Plan: `docs/plans/2026-04-30-003-feat-pwa-install-invitation-plan.md`

---

## 1. Build & Service-Worker Health

- [ ] `npm run build` exits 0
- [ ] `dist/sw.js` and `dist/workbox-*.js` exist
- [ ] `dist/unregister-sw.html` exists and is reachable from the build output
- [ ] `dist/manifest.webmanifest` is byte-identical to `public/manifest.webmanifest` (no plugin regeneration)
- [ ] `npm run preview` serves the app on `http://localhost:4173/carburants-france/`
- [ ] Chrome DevTools → Application → Service Workers shows an active SW at scope `/carburants-france/`
- [ ] Chrome DevTools → Application → Manifest reports the app as **Installable** (manifest valid + SW active)
- [ ] Lighthouse PWA audit (Chrome DevTools) passes the **Web app manifest and service worker meet the installability requirements** check
- [ ] After deploying to GH Pages, `curl -I https://lingelo.github.io/carburants-france/sw.js` reports a `Cache-Control` header. Record the measured value here: `_______________` (sanity check the plan's 600s assumption)

## 2. Capability Detection — UA Fixtures

In an open `npm run preview` tab, override `navigator.userAgent` via DevTools (or use device emulation), reload, click the install link in the welcome overlay, and verify the modal copy matches the expected `InstallContext`.

| User-Agent | Expected `InstallContext` | Modal copy hint | Result |
|---|---|---|---|
| `Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36` (with captured `beforeinstallprompt`) | `native-prompt` | Native browser dialog (not the modal) | |
| `Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36` (no captured prompt) | `generic` | "Utilisez le menu de votre navigateur…" | |
| `Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36` (with captured prompt) | `native-prompt` | Native dialog | |
| `Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:130.0) Gecko/20100101 Firefox/130.0` | `generic` | "Utilisez le menu de votre navigateur…" — **must not** mention Safari Share | |
| `Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0` | `generic` (or `native-prompt` if Edge captures the event) | Generic | |
| `Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1` | `ios-safari` | "Appuyez sur le bouton Partager…" | |
| `Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Version/17.0 Mobile/15E148 Safari/604.1` | `ios-safari` | iOS Share copy | |
| `Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 ... FBAV/450.0.0.0` (Facebook iOS WebView) | `in-app-webview` | "Ouvrez ce site dans Safari (iOS) ou Chrome (Android)" | |
| `Mozilla/5.0 (Linux; Android 14; ...) ... FB_IAB/MESSENGER;FBAV/...` (Facebook Android WebView) | `in-app-webview` | Same Safari/Chrome copy (bilingual) | |

## 3. Platform Walkthroughs

For each platform below, run the full sub-checklist. Record screenshots of the welcome overlay and (where applicable) the install instructions modal — these are required in the PR description.

### 3.1 Chrome desktop

- [ ] Welcome overlay shows the install link below "ou tapez une ville ci-dessus", visually quieter than the primary "Me localiser" CTA
- [ ] Microcopy "Lancement instantané depuis l'écran d'accueil" is **visible** (not a hover-only tooltip)
- [ ] Listener race: hard-reload with DevTools throttling Slow 4G + CPU 4× — `beforeinstallprompt` is still captured (verify via `localStorage.pwa_install_link_clicks` increments after a click)
- [ ] Click triggers the native install prompt on Chrome desktop (when installability criteria + engagement are met)
- [ ] Prompt → "Cancel": the link stays visible, a second click opens the **modal** (fallback to generic copy after consume)
- [ ] Prompt → "Install": the link disappears immediately, `localStorage.pwa_installed === '1'`, `localStorage.pwa_install_accepted` incremented
- [ ] Cross-tab install: open app in two tabs, install from tab A, switch to tab B without reload — the link disappears within ~1s
- [ ] Visibility-change re-check: with the app installed in another window, background the current tab, then bring it back — the link is no longer shown (sync from storage on visibility)
- [ ] Selecting a city makes the welcome overlay (and the link) disappear; selecting a fuel works as before — no regression

### 3.2 Chrome Android

- [ ] Welcome overlay sized correctly on a real or emulated Pixel device
- [ ] iPhone SE viewport (375×667 portrait): all 3 actions visible, no overflow into the bottom-sheet / fuel filter strip
- [ ] Click triggers the native A2HS / "Install app" prompt
- [ ] After install, opening the installed app in standalone mode hides the link entirely

### 3.3 Safari iOS

- [ ] Welcome overlay renders correctly on iPhone Safari
- [ ] Click opens the modal with title "Installer l'application" and body containing "Appuyez sur le bouton **Partager**" + share icon
- [ ] **Fermer** button (French) closes the modal — Escape and tap on the scrim also work
- [ ] Modal animation uses Radix defaults; no jank
- [ ] After manual Add-to-Home-Screen install, opening the app from the home screen launches in standalone mode → welcome overlay no longer shows the link

### 3.4 In-app WebView (Facebook / Instagram / Gmail)

- [ ] Open the GitHub Pages URL via a Facebook/Instagram DM on iOS → click invite → modal shows "Cette page est ouverte dans une application. Pour l'installer, ouvrez-la d'abord dans Safari (iOS) ou Chrome (Android)."
- [ ] Same on Android (FB_IAB UA) — modal copy is the same bilingual message
- [ ] No instructions about Safari's Share button appear on Android in-app

## 4. Recovery Page

- [ ] Visit `https://lingelo.github.io/carburants-france/unregister-sw.html` while the SW is active
- [ ] Page renders the "Réinitialisation du service worker…" message
- [ ] After ~500ms the page redirects to `/carburants-france/`
- [ ] Chrome DevTools → Application → Service Workers shows no registration after redirect
- [ ] Cache Storage is empty for the origin

## 5. Data freshness invariants

- [ ] DevTools → Application → Cache Storage: no `/carburants-france/data/**.json` URLs are cached (NetworkOnly rule)
- [ ] Force-reload the app, then check Network tab: department JSON requests go to network every time (no `(ServiceWorker)` source)
- [ ] After ~10 minutes (or by changing `meta.json` server-side), reload the app: the new data appears without a manual SW update

## 6. Accessibility spot checks

- [ ] Tab order in the welcome overlay: Me localiser → ou tapez une ville → Installer l'application
- [ ] Enter on the install link triggers the click
- [ ] When the modal is open, focus is trapped inside it (tab cycles through Fermer)
- [ ] Closing the modal restores focus to the install link
- [ ] Screen reader (VoiceOver / NVDA) announces the dialog title "Installer l'application" on open
- [ ] `prefers-reduced-motion: reduce`: modal opens without zoom animation (Radix default)
