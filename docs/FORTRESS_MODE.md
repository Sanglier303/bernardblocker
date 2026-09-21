# Mode Forteresse (Android Device Owner)

Le mode Forteresse est optionnel. Bernard fonctionne sans lui, mais un Android normal laisse
toujours au propriétaire du téléphone quelques chemins système impossibles à supprimer totalement
depuis une simple application.

Lorsqu'il est provisionné comme **Device Owner**, Bernard applique en plus :

- blocage système de la désinstallation de Bernard ;
- désactivation du contrôle utilisateur de Bernard (force-stop / clear-data sur les versions qui
  prennent en charge cette politique) ;
- interdiction du mode sans échec ;
- interdiction d'ADB / des fonctions de débogage ;
- interdiction de la réinitialisation depuis les Paramètres Android ;
- interdiction des installations depuis des sources inconnues (à relâcher avec le PIN avant une mise à jour sideloadée) ;
- interdiction d'ajouter ou de changer d'utilisateur ;
- Android 15+ : interdiction de créer un Espace privé ;
- interdiction de modifier manuellement date/heure/fuseau ;
- heure et fuseau automatiques lorsque l'API Android le permet.

## Attention avant provisioning

Le provisioning Device Owner est une opération d'administration Android. Sur un téléphone déjà
utilisé, il faut généralement repartir d'un appareil fraîchement réinitialisé, sans compte ni autre
utilisateur/profil de travail. Sauvegarde les données avant toute réinitialisation.

Installe d'abord l'APK Bernard signé définitivement, puis, pendant la préparation du téléphone :

```bash
adb shell dpm set-device-owner com.local.focusfence/.security.BernardDeviceAdminReceiver
```

Ensuite ouvre Bernard, configure le PIN administrateur, active Accessibilité et Accès aux données
d'utilisation. Le service réapplique automatiquement les politiques Forteresse quand il démarre.

## Récupération

Le menu Bernard > Paramètres, protégé par le PIN, contient une commande pour **relâcher les
politiques Forteresse**. Cela retire les restrictions appliquées par Bernard, mais ne constitue pas
forcément une procédure universelle de suppression du statut Device Owner sur toutes les versions
Android.

Ne provisionne pas ce mode si tu n'acceptes pas qu'une récupération complète puisse nécessiter ADB,
les outils d'administration Android ou une réinitialisation de l'appareil.
