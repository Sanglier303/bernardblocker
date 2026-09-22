# Bernard 0.4.6 — audit des quotas et faux blocages

Analyse initiale : `38b335551b225851af6865c47fb39ef36fb22cac` (0.4.4). Intégration finale sur `36359e890284ce238c635c2dfb0e2294b33aa757` (0.4.5). Correction : 0.4.6, versionCode 13.

## Symptôme et degré de certitude

Le signalement est un refus d'accès aux Reels alors qu'un compteur semble encore disponible. Plusieurs défauts du code peuvent produire ce symptôme ou une impression analogue. Ils ont été corrigés sans considérer que le diagnostic exact du téléphone serait établi : aucun journal du blocage réellement subi, arbre d'accessibilité Instagram réel ou essai sur le téléphone de l'utilisateur n'était disponible pendant cet audit.

## Défauts corrigés

### 1. Un événement d'heure n'est pas en lui-même une tentative de contournement

Le receiver verrouillait immédiatement et durablement Bernard à chaque ACTION_TIME_CHANGED / ACTION_TIMEZONE_CHANGED, même sans discontinuité réelle. Android peut émettre ACTION_TIME_CHANGED après une synchronisation automatique. Le nouveau ClockPolicy compare l'heure murale avec le temps monotone attendu et le fuseau réellement utilisé. Une correction automatique d'au plus cinq secondes, dans le même fuseau et sans changer la date attendue, ne verrouille plus le service et ne réinitialise pas le compteur. La limite de cinq secondes existait déjà pour le contrôle de dérive du chronomètre ; elle devient explicite et testée.

Le fuseau réellement modifié, un changement de date même minime, une grande dérive, une horloge monotone incohérente ou les réglages d'heure/fuseau automatiques désactivés restent protégés. Le contrôle de discontinuité est aussi effectué lorsque l'utilisateur ne regarde pas de Reels. Les anciens verrous ne sont pas effacés automatiquement : leur origine ne peut pas être reconstruite avec certitude. Le propriétaire doit utiliser « Réactiver après vérification » après rétablissement des protections nécessaires.

Référence Android : https://source.android.com/docs/core/connect/time — ajustements automatiques et applications à l'écoute d'ACTION_TIME_CHANGED.

### 2. Un onglet Reels sélectionné pouvait l'emporter sur une conversation

Le classificateur traitait le simple état sélectionné de l'onglet Reels avant les marqueurs explicites d'une conversation, d'un profil ou d'une publication isolée. Un état d'onglet conservé en arrière-plan pouvait donc faire consommer leur temps comme des Reels. Ces zones utilitaires prennent maintenant le pas sur ce seul indice faible. Un véritable lecteur Reels plein écran, notamment ouvert depuis un message ou depuis le fil, reste contrôlé. Les commentaires, partages, création et notifications conservent leurs exemptions. Les tests utilisent la même décision de classification que la production, pas une copie simplifiée.

Cela corrige une combinaison de marqueurs reproductible ; cela ne prétend pas connaître toutes les variantes de l'interface Meta. Les applications Lite/clones gardent leur traitement global plus strict.

### 3. Une seule décision pour le quota, les horaires et le verrou

La version 0.4.5 intégrée en parallèle fournit déjà AccessPolicy / AccessEvaluator. Ils sont conservés comme décision commune plutôt que remplacés par une deuxième politique. Le quota épuisé reste prioritaire sur les horaires. Les états couverts sont autorisé, anti-contournement, blocage permanent, permission manquante, hors horaires et quota atteint. Le service, la vérification des redirections Instagram et les cartes de compteurs utilisent ces règles communes. Une règle Jeux ou application entière garde sa priorité sur le quota social. Elle n'est pas supprimée silencieusement parce qu'il reste du temps social.

### 4. Un ancien écran de blocage pouvait survivre à sa cause

La présence d'un overlay interrompait systématiquement l'évaluation périodique. Le service vérifie maintenant que sa règle et son motif sont toujours applicables. L'ouverture de la plage horaire, un nouveau jour, une augmentation/désactivation authentifiée de la limite ou le retrait de la règle font disparaître l'ancien écran. Un autre motif de blocage n'est pas transformé en autorisation : il sera appliqué avec sa propre explication lors du nouvel accès.

### 5. Les indications « compteur actif » et « temps restant » étaient trompeuses

La sélection d'une source était affichée comme un comptage actif même si l'accès était refusé. L'indicateur reflète maintenant l'autorisation de compter et s'arrête hors de la surface sociale, à l'interruption et à la déconnexion du service. L'accueil se rafraîchit toutes les secondes au lieu de cinq. Le solde utilise des secondes ou min:s plutôt que de présenter une seconde restante comme une minute entière. L'arrondi reste au plus à la seconde et l'enforcement reste en millisecondes.

### 6. Attribution des derniers intervalles autour de minuit

Le journal utilisait la date courante pour certains intervalles au lieu de leur date réelle, ne partageait qu'une seule frontière de minuit et ignorait une journée déjà clôturée par une lecture concurrente de l'accueil. Les intervalles sont désormais répartis sur chaque date locale effective. Un intervalle tardif peut compléter hier sans charger aujourd'hui. La clôture intervient après l'ajout de la dernière fraction de lecture ; l'éligibilité du suivi continu à minuit est conservée. Un total corrigé réévalue le résultat de la journée. Le nombre de jours réussis peut être rectifié ; les images déjà déverrouillées ne sont pas confisquées. Les données de consommation ne sont jamais écrasées pour les ramener à une nouvelle limite.

Les durées pathologiques et débordements sont refusés explicitement. Aucun crédit historique consommé à tort par une ancienne version n'est effacé à l'aveugle.

### 7. État du service et redirection Instagram

Une reconnexion pouvait annuler le callback de vérification tout en conservant le drapeau « redirection Instagram en cours ». Le drapeau, ses callbacks et les overlays sont réinitialisés ensemble lors de la reconnexion/interruption/déconnexion. L'indication de service actif est retirée immédiatement à la déconnexion, sans attendre onDestroy. La distinction entre révocation réelle de l'accessibilité et déconnexion Android pendant une mise à jour est conservée.

### 8. Diagnostic du vrai motif, pas une supposition après coup

Un journal local conserve les vingt derniers refus avec date, package, périmètre (social/Jeux/application), surface détectée, cause, temps utilisé, quota et horaires. Les événements identiques très rapprochés sont regroupés. L'accueil affiche le dernier refus comme un événement passé, avec sa cause et son solde à cet instant, jamais comme une interdiction encore active. L'export diagnostic v2 ajoute ces décisions, les règles globales et les sources sociales sélectionnées.

Aucun texte de message, URL consultée, compte social, PIN ou secret de signature n'est ajouté. L'export reste volontaire ; il n'y a pas d'envoi automatique. Le nom technique d'une application fait partie des données locales exportées.

## Revue transversale et protections conservées

Périmètre : décision de blocage, détection sociale/navigateur, journal et compteur historique, cartes et éditeurs, cycle de vie du service/PIN, protections des écrans système, autorisations, validation des mises à jour et chaîne de compilation/publication.

Le PIN propriétaire, son stockage, la protection contre les overlays malveillants, les contrôles des écrans de désactivation, le rôle administrateur/Forteresse et la signature permanente ne sont pas remplacés. Les changements de configuration restent soumis au PIN. Les tests d'URL HTTPS, package, version, minimum Android et certificat des mises à jour restent exécutés. Aucun assouplissement de ces validations ni des contrôles CI n'est inclus.

Un audit de code n'établit pas l'inviolabilité : root, restauration système, récupération physique et toutes les politiques constructeur restent hors de ce résultat. Le navigateur conserve volontairement sa dernière surface sociale connue lorsque la barre d'adresse disparaît ; ce compromis anti-contournement peut nécessiter des diagnostics sur de nouvelles interfaces. Un écran Meta inconnu reste distinct d'un fil positivement reconnu pour ne pas sacrifier les messages.

## Validation reproductible et limites

Vérification locale : 124 méthodes des tests de logique exécutées avec javac --release 17 et un harnais de réflexion. Les stubs Android lèvent une erreur si utilisés : seules les décisions pures sont validées ainsi. Ce contrôle n'est ni Gradle/JUnit, ni un émulateur Android.

Tests ajoutés : 14 cas d'horloge, 4 cas d’affichage et 7 cas de détection Instagram. Cinq parcours d'instrumentation supplémentaires vérifient l'indicateur au repos/déconnexion, le diagnostic borné et trois cas d'attribution à minuit/dates multiples. L'édition simulée dans le test de service n'est pas présentée comme une validation du parcours PIN ; les tests PIN Android existants continuent à exercer la véritable saisie et les retours système.

Les corrections d’overlay, de saisie des horaires, de sauvegarde atomique et de course PIN de 0.4.5, ainsi que leurs tests, sont intégralement conservées. Les sections 3 et 4 décrivent donc des corrections déjà apportées en parallèle, pas de nouveaux changements exclusifs à 0.4.6.

La validation complète doit être lue dans le run du commit livré : tests unitaires Gradle, lint debug/release, compilation debug/release et instrumentation API 26, API 35 et API 36. Un APK de production n'est publié par le workflow qu'après toutes ces validations. Aucun résultat CI n'est présumé dans ce document. Les variantes Instagram réelles sur le téléphone et un cycle de mise à jour entre deux versions sur celui-ci ne sont pas démontrés par ces tests.
