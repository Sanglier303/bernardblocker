# 0.4.6 — Faux blocages et cohérence des compteurs

- Préserve les corrections d’horaires, d’éditeur, d’overlay et de PIN de 0.4.5.
- Ne transforme plus les petites corrections automatiques d’heure en verrou durable ; les changements suspects restent protégés.
- Empêche un ancien onglet Reels sélectionné de faire compter une conversation/profil/publication isolée.
- Corrige l’attribution des intervalles à minuit et l’état après déconnexion du service.
- Affiche le solde en secondes, le vrai motif du dernier refus et un diagnostic local borné.
- Voir docs/AUDIT-v0.4.6.md pour la portée et les limites de validation.

## 0.4.5

- Réévalue les écrans de blocage après modification des règles et à l'ouverture naturelle d'une plage.
- Partage les décisions entre le blocage initial, les overlays et la vérification différée Instagram.
- Ne confond plus durée vide/invalide et quota illimité, ni heures provisoirement égales et journée entière.
- Sauvegarde les groupes en une transaction sans remettre les compteurs à zéro.
- Conserve les autres règles et le verrou anti-contournement lors d'une modification de plage.
- Corrige le diagnostic de comptage et ajoute le dernier motif de blocage à l'export local.
- Étend la matrice de tests Android à l'API 36; détails dans docs/AUDIT-v0.4.5.md.

## 0.4.4

- Corrige les boucles PIN liées aux événements de fenêtre retardés, à la réutilisation de PinActivity et au retour des autorisations Android.
- Vérifie l'activation réelle du rôle administrateur dans les parcours de test, pas seulement l'apparition de son écran.
- Corrige des faux positifs de détection sociale et partage les décisions entre la production et les tests.
- Préserve le temps consommé lorsque le quota change; corrige la reprise du comptage après redémarrage et les transitions écran/activité.
- Refuse qu'un sous-menu « Installer des applications inconnues » présent dans les informations d'application autorise l'accès à Forcer l'arrêt.
- Rend l'interface Forteresse sans effet de bord et précise l'état du service réellement connecté.
- Fiabilise les tâches de mise à jour, les vérifications d'APK, les retours d'autorisation et de PackageInstaller.
- Ajoute l'export volontaire d'un diagnostic local sans PIN, clés ou contenu des messages.
- Réserve la clé permanente aux APK release non débogables. Étend la CI à Android API 26/35 et à Lint debug/release; publication après toutes les validations.

Voir `docs/AUDIT-v0.4.4.md` pour le périmètre, les défauts confirmés et les limites de validation.

## 0.4.3

- Corrige la boucle de code PIN lors de l’activation de Bernard comme administrateur de l’appareil sur Pixel/Android.
- Reconnaît le passage Settings ↔ PermissionController comme un seul flux Device Admin strictement scoped.
- Conserve l’autorisation système temporaire pendant les transitions d’activité, puis la révoque en quittant les pages protégées.
- Ajoute un test Android réel qui ouvre l’écran Device Admin et vérifie que le PIN ne réapparaît pas.

## 0.4.2

- Ajoute les mises à jour automatiques depuis GitHub Releases.
- Télécharge les nouvelles APK en arrière-plan et vérifie package, version, SHA-256 et certificat Bernard avant installation.
- Ajoute un flux PackageInstaller avec confirmation Android seulement lorsque le système l’exige.
- Réserve une autorisation anti-bypass temporaire au seul flux système de mise à jour.
- Le réseau sert uniquement aux mises à jour GitHub, sans télémétrie.

# Changelog

## 0.4.1

- Audit anti-contournement approfondi.
- Sessions PIN raccourcies et invalidées dès que Bernard perd le premier plan ou quitte un écran protégé.
- Vérification du PIN répétée au moment exact de chaque action sensible, afin d'éviter une course entre l'expiration du code et un bouton déjà affiché.
- Verrouillage après erreurs PIN basé sur l'horloge monotone Android, résistant aux changements d'heure et aux redémarrages.
- Autorisations temporaires des écrans système limitées au flux demandé : Usage Access ne déverrouille plus App Info, désinstallation ou arrêt forcé.
- L'écran PIN refuse les overlays tiers sur Android 12+.
- Le garde anti-tamper reste actif même lorsqu'un écran de blocage Bernard est déjà visible.
- Redirection Instagram après quota vérifiée rapidement ; si les DM ne sont pas réellement atteints, Bernard renvoie à l'accueil.
- Détection navigateur durcie : validation du vrai domaine, latch monotone sans expiration lorsque la barre d'adresse est masquée.
- Instagram inconnu repasse en fail-closed après les exemptions explicites DM/profil/commentaires/création.
- Facebook conserve l'état Feed lorsque la barre de navigation disparaît pendant le scroll, tout en libérant les écrans utilitaires explicites.
- Les retards de handler sont désormais comptés dans le quota au lieu de devenir du temps gratuit.
- Les conteneurs de clonage sont bloqués pour toute protection active, pas uniquement le scroll social.
- Le replay de l'onboarding ne permet plus d'atteindre les autorisations sans PIN.
- Détection et verrouillage des changements manuels d'heure ou de fuseau lorsque les réglages automatiques sont désactivés.
- Protection optionnelle Device Admin contre la désinstallation, avec détection de sa désactivation.
- Mode Forteresse renforcé : installation, désinstallation et contrôle d'apps interdits, suspension fail-closed des apps surveillées si l'accessibilité Bernard est coupée.
- Interception du sélecteur d'utilisateur et de l'entrée Private Space afin d'éviter un profil où Bernard n'est pas actif.
- Nouveaux tests natifs couvrant les pages protégées, expiration PIN, portée des autorisations système et tentative d'ouverture de Settings pendant un overlay.

## 0.4.0

- Ajout d'un **code administrateur local** avant utilisation de Bernard. Le code n'est pas compilé dans l'APK : il est choisi sur le téléphone et seul un vérificateur PBKDF2 salé est conservé dans le stockage privé.
- Les limites, la sélection d'applications, les autorisations et les réglages sont protégés par ce code.
- 5 codes erronés déclenchent un verrouillage persistant de 30 secondes.
- Fermer ou retirer l'interface Bernard des applications récentes ne coupe pas le service d'accessibilité.
- Protection des écrans Android permettant de forcer l'arrêt, effacer les données, retirer les autorisations ou désinstaller Bernard quand ces écrans ciblent explicitement Bernard.
- Les quotas Jeux et applications finies passent en **fail-closed** si l'accès aux données d'utilisation est retiré.
- Détection des changements d'heure/fuseau et des raccourcis d'accessibilité pouvant contourner la protection.
- Couverture renforcée du scroll : Instagram Fil/Explore/Reels/Stories/recherche par hashtags/détails de posts, Facebook Feed/Reels/Stories/Watch, YouTube Shorts, TikTok et Threads.
- Les DM Instagram, commentaires, notifications, création et profils restent utilisables. Ouvrir du contenu réel depuis ces surfaces peut néanmoins consommer le quota.
- Détection des versions Lite, clients alternatifs évidents et handlers de liens sociaux.
- Détection des sites sociaux dans les navigateurs installés.
- Nouveau système de signature : une clé de release permanente est exigée pour les builds main et son certificat SHA-256 est épinglé dans le dépôt.
- Tests natifs anti-contournement : fermeture de l'Activity, retrait de Usage Access, protection de l'écran App Info et verrouillage PIN.

## 0.3.2

- Les messages privés Instagram restent réellement interactifs après quota.
- Priorité aux écrans utilitaires : commentaires, partage, création, notifications, profils et listes d'abonnements.
- Facebook ne considère plus tout écran inconnu comme le fil.

## 0.3.1

- Ajout du Fil et d'Explore Instagram au quota de scroll.
- Diagnostic de surface visible dans Bernard.

## 0.3.0

- Nouvelle interface Bernard Bloqueur.
- Récompenses, historique et écrans illustrés.
