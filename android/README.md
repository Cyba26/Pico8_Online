# Pico8 Online Android App

Une application Android qui permet de jouer aux jeux PICO-8 en ligne et hors ligne.

## Fonctionnalités

- **Navigation WebView**: Interface native avec une WebView pour afficher l'application web Pico8_Online
- **Cache local**: Tous les fichiers (pico8.dat, cartouches, assets) sont téléchargés et stockés localement
- **Mode hors ligne**: Fonctionne sans connexion Internet une fois les fichiers téléchargés
- **Synchronisation automatique**: Mise à jour du contenu quand la connexion revient
- **Détection de connectivité**: Basculer automatiquement entre le mode en ligne et hors ligne

## Structure du projet

```
Pico8_Android/
├── app/
│   ├── src/main/
│   │   ├── java/com/pico8/online/
│   │   │   ├── CacheEntity.kt          # Entité Room pour le cache
│   │   │   ├── CacheDao.kt             # DAO Room
│   │   │   ├── CacheDatabase.kt        # Base de données Room
│   │   │   ├── CacheManager.kt         # Gestionnaire de cache
│   │   │   ├── Config.kt                # Configuration
│   │   │   ├── ConnectivityReceiver.kt # Détection des changements de réseau
│   │   │   ├── CustomWebViewClient.kt  # Client WebView personnalisé
│   │   │   ├── MainActivity.kt          # Activité principale
│   │   │   ├── Pico8App.kt             # Application
│   │   │   ├── SyncService.kt          # Service de synchronisation
│   │   │   └── CacheConverters.kt      # Convertisseurs Room
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   └── activity_main.xml
│   │   │   ├── values/
│   │   │   │   ├── colors.xml
│   │   │   │   ├── dimens.xml
│   │   │   │   ├── strings.xml
│   │   │   │   └── styles.xml
│   │   │   └── ...
│   │   └── assets/
│   │       ├── index.html
│   │       ├── cartouches.json
│   │       └── cartouches/
│   └── build.gradle
├── build.gradle
├── settings.gradle
└── README.md
```

## Configuration

### URL de base

L'application utilise par défaut: `https://cyba.github.io/Pico8_Online`

Vous pouvez modifier l'URL dans:
- `Config.kt` (constante `DEFAULT_BASE_URL`)
- `CacheManager.kt` (méthode `getBaseUrl()`)

### Développement local

Pour tester avec un serveur local:
1. Lancer le serveur de développement: `npm run dev` dans Pico8_Online
2. Modifier `getBaseUrl()` pour retourner `http://10.0.2.2:5173` (Android Emulator)
3. Autoriser le cleartext traffic dans `network_security_config.xml`

## Build

```bash
# Clone le projet
cd Pico8_Android

# Build avec Gradle
./gradlew assembleDebug

# Ou build & installer sur un appareil
./gradlew installDebug
```

## Architecture

1. **WebView avec cache**: Utilisation d'un `CustomWebViewClient` qui intercepte les requêtes et sert le contenu depuis le cache local
2. **Synchronisation**: Service en arrière-plan qui met à jour le cache quand la connexion revient
3. **Room Database**: Stockage des fichiers cachés avec chiffrement Base64
4. **OkHttp**: Téléchargement des fichiers depuis Internet
5. **BroadcastReceiver**: Détection des changements de connectivité

## Dépendances

- AndroidX (AppCompat, Room, Lifecycle)
- Kotlin Coroutines
- OkHttp 3

## Prochaines améliorations

- [ ] Gestion plus intelligente de la synchronisation (delta updates)
- [ ] Notification quand de nouveaux jeux sont disponibles
- [ ] Gestion des erreurs plus robuste
- [ ] Interface utilisateur plus aboutie
- [ ] Support des téléchargements de cartouches depuis l'appareil
- [ ] Configuration utilisateur (choix de l'URL, etc.)

## Licence

Ce projet est basé sur Pico8_Online: https://github.com/cyba/Pico8_Online
