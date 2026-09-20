# FocusFence 0.1.1

Prototype Android local de contrôle d'usage.

Fonctions:
- quota/plage horaire communs pour un groupe Jeux;
- quota/plage communs Instagram Reels + Stories, Facebook Reels + Stories et YouTube Shorts;
- le reste des applications sociales reste accessible;
- compteur local quotidien;
- aucun accès Internet requis;
- diagnostic Logcat pour recalibrer les détecteurs.

## Build
JDK 17, Android SDK 35, Gradle Wrapper 8.9.

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

Après sideload, activer FocusFence dans Accessibilité et autoriser l'accès aux données d'utilisation pour les quotas Jeux.
