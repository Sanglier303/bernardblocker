# Changelog

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
