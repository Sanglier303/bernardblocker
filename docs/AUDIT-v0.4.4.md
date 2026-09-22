# Audit fonctionnel Bernard 0.4.4

Base examinée : 0.4.3, commit 68e6d8dda42cb15b9eb68691ad5ee089d10f33d0. Version de correction : 0.4.4, versionCode 11.

## Pourquoi les anciennes validations ne suffisaient pas

Les 19 tests précédents n'établissaient pas que tous les parcours utilisateur fonctionnaient. Le test Device Admin autorisait directement le contrôleur, attendait le premier écran externe puis sortait immédiatement. Il ne saisissait pas le PIN, n'activait pas réellement le rôle et ne vérifiait pas le retour dans Bernard. Les tests de détection Instagram utilisaient une copie simplifiée du classificateur, pas exactement la décision de production. Les captures étaient récupérées après la désinstallation automatique effectuée par Gradle, ce qui expliquait leur absence dans certains runs.

Cet audit remplace ces angles morts par des parcours plus complets et des décisions pures partagées entre les tests et la production. Un succès CI reste une validation de ces scénarios, pas une preuve d'absence de tout bug sur tous les téléphones.

## Défauts confirmés et modifications

### PIN et autorisations Android

- Le traitement PermissionController ajouté en 0.4.3 pour Device Admin était placé après un retour qui rejetait déjà les paquets non Settings. Cette branche était donc inaccessible. Elle est désormais évaluée dans le bon ordre, uniquement pour le flux d'administration.
- Le service réagissait à des événements provenant d'une ancienne fenêtre alors que Bernard ou son clavier PIN était déjà au premier plan. Le paquet et l'identifiant de fenêtre sont maintenant confrontés à la fenêtre active. La vérification périodique de la fenêtre active est conservée.
- Des événements de widgets remplaçaient la classe d'activité mémorisée. Les classes d'activité et les événements de composants sont désormais distingués.
- PinActivity était singleTop sans actualiser son contexte dans onNewIntent. Une nouvelle requête pouvait réutiliser l'ancien écran cible ou le mauvais périmètre d'autorisation. Le contexte est réinitialisé à chaque nouvelle intention.
- Un nouveau contrôle PIN conservait parfois le mauvais périmètre temporaire et provoquait une nouvelle boucle après validation. Une nouvelle authentification propriétaire autorise le paquet système effectivement demandé. Les permissions accordées depuis Bernard restent limitées à leur flux.
- Le retour d'un écran Android déclenchait immédiatement un nouveau PIN, car la session d'administration avait été fermée lors de la sortie. Les parcours lancés par Bernard utilisent désormais un retour explicite, révoquent l'autorisation système et reviennent à l'accueil au lieu de redemander automatiquement le code.
- L'écran PIN devient défilable sur petits écrans et les callbacks sont retirés quand il n'est plus visible. Les protections FLAG_SECURE, masquage des overlays et filtrage des touches obscurcies restent actives.

### Détection sélective et compteurs

- Instagram classait tout écran inconnu comme un fil à contrôler. Un écran inconnu ou une publication isolée n'est plus assimilé arbitrairement au fil. Les marqueurs explicites Feed, Explore, Reels et Stories restent contrôlés. Les messages et interfaces utilitaires restent prioritaires.
- L'onglet de notifications non sélectionné pouvait suffire à considérer le fil comme une page autorisée. La sélection réelle est désormais requise. Les marqueurs de création trop génériques ont été retirés.
- Facebook évaluait certains marqueurs Reels avant les interfaces de commentaires ou de partage. L'ordre et la durée de conservation des décisions ont été corrigés.
- Le navigateur pouvait utiliser une URL en cours de saisie ou une décision issue d'une autre fenêtre. La détection requiert un champ d'adresse connu, visible et non en cours d'édition; les décisions temporaires sont liées à la fenêtre.
- Des nœuds d'accessibilité n'étaient pas libérés sur plusieurs parcours. Un visiteur borné centralise maintenant la libération, y compris lors d'un retour anticipé.
- Le journal plafonnait la consommation au quota courant. Abaisser le quota pouvait ainsi effacer du temps déjà consommé. La consommation observée est désormais conservée indépendamment de la limite.
- Le calcul des usages gérait mal une extinction/réactivation de l'écran et certains événements de pause tardifs entre deux activités de la même application. Les chronologies sont triées et les identités d'activité sont prises en compte.
- Un groupe Jeux limité seulement par ses horaires exigeait malgré tout l'accès aux usages. Cette permission reste obligatoire pour une limite de durée, pas pour un quota illimité soumis seulement à une plage horaire.
- Des valeurs de préférences invalides pouvaient devenir illimitées ou provoquer des erreurs. Les bornes sont vérifiées; une valeur invalide déclenche un état explicite de protection plutôt qu'une autorisation illimitée silencieuse.

### Forteresse, état affiché et diagnostic

- La consultation des Paramètres réappliquait immédiatement les politiques Forteresse que le propriétaire venait de relâcher. L'affichage devient sans effet de bord, avec actions explicites pour appliquer ou relâcher les politiques.
- L'état de protection distingue désormais un service réellement connecté d'une autorisation simplement cochée. La levée du verrou anti-contournement vérifie les autorisations nécessaires et le rôle administrateur précédemment accordé.
- La fermeture du service pendant une mise à jour n'est plus assimilée automatiquement à une révocation de l'accessibilité; l'état réel de l'autorisation est vérifié.
- Une exportation volontaire de diagnostic local indique version, Android, modèle, permissions, état du service, quotas, fenêtres horaires, dernier diagnostic de détection et état de mise à jour. Elle n'exporte ni PIN, ni hash du PIN, ni clés, ni contenu des messages. Elle n'est jamais envoyée automatiquement.

### Mises à jour GitHub

- Les contrôles périodiques sont confiés à JobScheduler plutôt qu'à un simple timer dépendant du service d'accessibilité. Android reste libre de différer une tâche pour économiser la batterie.
- Les permissions réseau et de redémarrage nécessaires sont déclarées, notamment ACCESS_NETWORK_STATE pour les tâches avec contrainte réseau sur Android récent.
- Les téléchargements sont bornés, limités aux hôtes HTTPS GitHub de livraison attendus, stockés en privé et vérifiés avant toute ouverture d'autorisation système. Le package, la version, le minimum Android, le SHA-256 et les certificats installés/téléchargés sont contrôlés. Android reste l'autorité finale pour la validation cryptographique et l'installation.
- Les téléchargements incomplets sont supprimés. Les vérifications simultanées sont regroupées et ne produisent plus un faux message 'à jour' pendant une autre vérification.
- L'installation manuelle reste utilisable lorsque les contrôles automatiques sont désactivés. Le refus d'une autorisation n'engendre pas de boucle.
- Les retours PackageInstaller sont associés à l'action, à la session et à la version attendues. Une confirmation reçue en arrière-plan est proposée au prochain passage au premier plan plutôt que lancée arbitrairement.
- Le mode courant détecte et télécharge automatiquement, puis propose l'installation. Android peut demander une confirmation. Une installation totalement silencieuse n'est pas garantie.

### Défauts supplémentaires révélés pendant la validation

- L'écran d'informations d'application d'Android 8 expose un sous-menu « Install unknown apps ». Le reconnaître par ce texte seul accordait à tort une autorisation de mise à jour à tout l'écran, avec Forcer l'arrêt. La présence d'actions destructives ou d'une classe d'informations d'application est désormais un veto prioritaire.
- Un événement DEVICE_STARTUP était traité comme DEVICE_SHUTDOWN. Sans nouvel événement SCREEN_INTERACTIVE, la chronologie restait inactive après le démarrage et pouvait ne plus compter les jeux. Le démarrage dispose maintenant d'un événement distinct et attend la prochaine activité au premier plan.
- La constante DISALLOW_GRANT_ADMIN n'est appliquée qu'à partir de l'API 34 qui la prend en charge.
- Un test du journal placé dix minutes avant l'heure courante débordait sur la veille lorsque la CI s'exécutait juste après minuit. Le test isole maintenant un intervalle au sein d'une seule journée; la répartition de production entre deux jours n'est pas désactivée.
- L'automatisation UI demande explicitement les identifiants de vues, reconnaît les libellés Android en majuscules et actionne le parent `restricted_action` auquel Android attache réellement le listener d'activation (vérifié dans l'arbre de l'émulateur et AOSP DeviceAdminAdd). Elle enregistre l'arbre réel de confirmation et une capture pour documenter les échecs, au lieu d'assouplir la validation du rôle administrateur.

### Chaîne de publication

- Les APK debug/test utilisent exclusivement une identité debug distincte. La clé permanente est réservée aux APK release non débogables, même sur main.
- Les secrets de signature ne sont plus exposés dans l'environnement de tout le job de branche. Ils sont limités aux étapes de signature de main, jamais aux PR.
- Les versions et noms d'artefacts sont dérivés des métadonnées de l'APK au lieu de chaînes 0.4.3 dispersées dans le workflow.
- Lint debug et release sont exécutés. La matrice de parcours Android couvre API 26 et 35. Le résultat complet de l'instrumentation et les captures sont conservés.
- La publication attend toutes les validations, assemble d'abord une release brouillon complète puis la publie. Une version existante issue d'un autre commit n'est jamais remplacée silencieusement.

## Validation et limites

Avant CI, 54 méthodes de tests des décisions pures ont été exécutées localement avec javac et un petit harnais de réflexion. Ce contrôle ne remplace ni JUnit/Gradle, ni Android. Les résultats effectifs de compilation, Lint et instrumentation doivent être lus dans le run attaché au commit livré.

Les nouveaux parcours d'instrumentation saisissent réellement le PIN, activent le rôle administrateur, vérifient le retour sans boucle, contrôlent la réutilisation de PinActivity, les refus de permissions, le quota Jeux illimité et les callbacks de mise à jour non correspondants. Les tests de détection vérifient des arbres simulés et des décisions partagées avec la production.

Non démontré par cette CI : comportement sur le Pixel physique de l'utilisateur; toutes les variantes d'Instagram/Facebook et leurs expérimentations serveur; provisioning Device Owner complet; cycle de mise à jour entre deux releases sur ce téléphone; tous les cas de corruption de stockage ou de récupération privilégiée Android. Un écran Instagram inconnu est volontairement distingué d'un fil identifié afin de ne pas sacrifier la messagerie. Si un nouveau fil perd ses marqueurs, il faut capturer son diagnostic pour adapter la détection.

Le PIN personnel existant, les réglages, les historiques et la clé permanente ne sont pas remplacés. Aucune promesse d'inviolabilité face au propriétaire système, à root ou à la récupération physique n'est faite.

## Références techniques

- Android AccessibilityEvent : https://developer.android.com/reference/android/view/accessibility/AccessibilityEvent
- Android JobInfo.Builder : https://developer.android.com/reference/android/app/job/JobInfo.Builder
- Android PackageInstaller.Session : https://developer.android.com/reference/android/content/pm/PackageInstaller.Session
- Android PackageInstaller.SessionParams : https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams
- Les défauts et corrections ci-dessus sont documentés par le diff depuis 68e6d8dda42cb15b9eb68691ad5ee089d10f33d0 et les tests ajoutés dans app/src/test et app/src/androidTest.
