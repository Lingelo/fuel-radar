# Guide de publication Play Store — FuelRadar (pas à pas)

Ce guide couvre **ce que toi seul peux faire** (compte Google, upload, clé Maps).
Il complète :
- `play-store-listing.md` — textes de la fiche (FR/EN/ES/PT)
- `play-store-data-safety.md` — formulaire Data Safety

> ⚠️ **Le piège à comprendre avant tout : Play App Signing.**
> Google re-signe ton app avec **sa** clé. Ta clé locale (`fuelradar-release.jks`)
> n'est qu'une clé d'**upload**. Résultat : le SHA-1 qui compte pour Google Maps
> en production n'existe **qu'après ton 1er upload**. C'est pourquoi la
> restriction de la clé Maps se fait **à l'étape 5**, pas avant. Symptôme si on
> se trompe : carte grise en prod alors qu'elle marche en local.

---

## 1. Compte Google Play Console

- https://play.google.com/console → payer les **25 $ (une fois)**.
- Créer une application : nom **FuelRadar**, langue par défaut FR, type *App*, gratuite.

## 2. Activer Play App Signing (par défaut, recommandé)

Rien à faire de spécial : au premier upload d'un `.aab`, Google propose
« *Let Google manage and protect your app signing key* » → **accepter**.
Tu gardes ta clé d'upload (`fuelradar-release.jks`) ; Google gère la clé d'app.

## 3. Premier upload (piste de test interne)

1. Play Console → ton app → **Test → Testeurs internes** → *Créer une release*.
2. Uploader le fichier :
   `apps/android/app/build/outputs/bundle/release/app-release.aab`
3. Renseigner les notes de version (ex. « Première version de test »).
4. Ajouter ton adresse e-mail comme testeur, enregistrer.

> On passe par le **test interne** d'abord : c'est instantané (pas de revue),
> ça valide que l'`.aab` est accepté, et ça débloque les SHA-1 pour la clé Maps.

## 4. Récupérer les deux SHA-1

Play Console → **Configuration → Intégrité de l'application → Signature de l'app**.
Tu y vois DEUX certificats :
- **Certificat de signature d'application** (clé de Google) ← celui qui compte en prod
- **Certificat de clé d'importation** (ta clé d'upload)

Copie le **SHA-1 de chacun**.

## 5. Restreindre la clé Maps (Google Cloud) — AJOUTER le SHA-1 de Google

> **Déjà fait** (via gcloud) : la clé `Maps Platform API Key`
> (uid `f388cc4d-d0d1-453f-9452-def4d7212c70`, projet `fuel-radar-503216`) est
> restreinte à l'API **Maps SDK for Android** + package `fr.fuelradar` avec le
> **SHA-1 d'upload** (`a62e0…`). Elle n'est plus ouverte.
>
> **Reste à faire ici** : y **ajouter** le SHA-1 de la clé d'app **Google**
> (récupéré à l'étape 4), sinon la carte reste grise pour l'app installée depuis
> le Store. On garde les deux entrées (upload + Google).

Remplace `SHA1_GOOGLE` par le SHA-1 de l'étape 4 (format avec deux-points), puis :

```bash
gcloud services api-keys update f388cc4d-d0d1-453f-9452-def4d7212c70 \
  --project=fuel-radar-503216 \
  --api-target=service=maps-android-backend.googleapis.com \
  --allowed-application=sha1_fingerprint=A6:2E:03:1B:91:C8:84:45:72:E0:3B:EB:0F:6B:4C:62:72:44:A6:AA,package_name=fr.fuelradar \
  --allowed-application=sha1_fingerprint=SHA1_GOOGLE,package_name=fr.fuelradar
```

(Équivalent en console : *APIs & Services → Credentials → Maps Platform API Key →
Application restrictions → Android apps → Add*.)

## 6. Compléter la fiche + Data Safety

- **Fiche principale** : titre, descriptions (voir `play-store-listing.md`),
  icône 512×512 (`docs/store-assets/play-icon-512.png`), feature graphic 1024×500,
  **≥ 2 captures d'écran** (depuis ton téléphone : Carte, Stations, Détail, Tendances).
- **Data Safety** : suivre `play-store-data-safety.md` (localisation précise,
  utilisée sur l'appareil, non partagée ; URL de confidentialité obligatoire).
- **Contenu** : classification (Tout public), pas de pub, pas d'achats intégrés,
  politique de confidentialité `https://lingelo.github.io/fuel-radar/privacy.html`.

## 7. Passer en production

Quand le test interne est validé (carte OK, app stable) :
Play Console → **Production → Créer une release** → réutiliser le même `.aab`
(ou en rebuilder un) → soumettre à l'examen Google (compter quelques jours).

---

## Rebuilder l'`.aab` plus tard

Local (nécessite `keystore.properties` + `local.properties` avec `MAPS_API_KEY`) :
```
cd apps/android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:bundleRelease
```
Ou via CI : onglet **Actions → Android → Run workflow** (secrets requis, voir
en-tête de `.github/workflows/android.yml`).

**À chaque nouvelle version** : incrémenter `versionCode` (+1) et `versionName`
dans `apps/android/app/build.gradle.kts` — Google refuse deux uploads au même
`versionCode`.
