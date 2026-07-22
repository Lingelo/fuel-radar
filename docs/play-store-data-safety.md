# Play Store — checklist Data Safety & release (FuelRadar Android)

Référence : plan `docs/plans/2026-07-22-004-feat-monorepo-android-native-app-plan.md` (U9).

## Prérequis (à faire par le mainteneur)

- [ ] Compte Google Play Console (frais uniques 25 $).
- [ ] Projet Google Cloud + **compte de facturation** (CB) pour la clé Maps.
- [ ] Clé Maps **prod** restreinte par *package name* `fr.fuelradar` + empreinte **SHA-1 de la clé de
      release** (voir ci-dessous), API restreinte à « Maps SDK for Android ».
- [ ] Keystore de release : `keytool -genkey -v -keystore fuelradar-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias fuelradar`
- [ ] `apps/android/keystore.properties` créé depuis `keystore.properties.example` (git-ignoré).
- [ ] `MAPS_API_KEY` (clé prod) dans `apps/android/local.properties` (git-ignoré).

Obtenir le SHA-1 de release :
`keytool -list -v -keystore fuelradar-release.jks -alias fuelradar`

## Build release

- Débogage local : `./gradlew :app:assembleDebug`
- AAB signé pour le Play Store : `./gradlew :app:bundleRelease`
  (nécessite `keystore.properties` ; sinon repli sur la signature debug).

## Formulaire Data Safety (Play Console)

Déclarations à cocher, **cohérentes avec `apps/web/public/privacy.html`** :

- **Localisation → Localisation précise** : collectée ? **Oui** (utilisée sur l'appareil),
  partagée ? **Non**. Finalité : *Fonctionnalité de l'app*. Traitement éphémère, non lié à l'identité.
- Aucune autre catégorie (pas de compte, pas d'identifiants, pas d'analytics).
- **Politique de confidentialité (URL obligatoire)** : `https://lingelo.github.io/fuel-radar/privacy.html`
- Chiffrement en transit : Oui (HTTPS).
- Suppression des données : sans objet (aucune donnée serveur ; données locales supprimées à la
  désinstallation).

⚠️ Google 15/04/2026 : privilégier la portée minimale de localisation ; ne pas demander la
localisation en arrière-plan (l'app ne le fait pas).

## Fiche store

- [ ] Icône adaptive (dérivée du logo Stitch `stitch_.../fuelradar_brand_logo`).
- [ ] Captures d'écran des écrans (Carte, Stations, Détail, Tendances).
- [ ] Description FR/EN/ES/PT (l'app est déjà localisée).
