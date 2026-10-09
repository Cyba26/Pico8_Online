# App Android

Le lanceur du site dans un WebView, pour jouer **hors ligne**. Téléchargeable sur
le site (bouton en haut à droite) : `public/releases/Pico8_Online.apk`.

## Comment ça marche

- Le site (`public/index.html`, `cartouches.json`, `cartouches/`) est **embarqué
  dans l'APK au build** : rien n'est dupliqué dans git, la source reste `public/`.
- Il est servi depuis une origine locale fixe (`appassets.androidplatform.net`) :
  le `pico8.dat` déposé une fois reste dans l'IndexedDB de l'app, réseau ou pas.
- `SiteStore` : la page et la liste des cartouches sont servies depuis la
  dernière copie connue puis rafraîchies en arrière-plan — une cartouche
  ajoutée au site apparaît dans l'app au lancement suivant, sans republier
  l'APK. Les cartouches sont gardées par empreinte (`?v=`, voir
  `scripts/build-cartouches.mjs`).
- Au lancement, l'app lit `releases/version.json` sur le site et propose
  (une fois par version) de télécharger le nouvel APK.

Le runtime PICO-8 n'est **pas** dans l'APK (fichier sous licence Lexaloffle) :
il faut déposer son `pico8.dat` au premier lancement, comme sur le site.

## Publier une version

Ne republier que si le code natif (`android/`) a changé — le contenu du site
se met à jour tout seul.

1. Monter `appVersionCode` (et `appVersionName`) dans `app/build.gradle.kts`.
2. `npm run build` à la racine (régénère `cartouches.json`).
3. `cd android && ./gradlew publishApk` → `public/releases/Pico8_Online.apk`
   + `version.json`.
4. Committer, pousser : Vercel déploie, les apps installées proposent la mise à jour.

## Signature

La clé est **hors git** : `android/release.jks` + `android/keystore.properties`
(`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). **La sauvegarder** :
sans elle, une nouvelle version ne s'installe plus par-dessus l'ancienne (il
faudrait désinstaller, et perdre le `pico8.dat` déposé).

## Outillage

JDK 17 et SDK Android (platform 35, build-tools 35) ; `local.properties`
indique `sdk.dir`. Sur ce Mac : `brew install openjdk@17
android-commandlinetools`, `JAVA_HOME=/opt/homebrew/opt/openjdk@17`.
