# Bernard Bloqueur 0.3.0

Application **Android native en Java**, sans WebView, sans serveur, sans compte et sans permission Internet. Interface française construite à partir des maquettes validées : papier crème, vert forêt, scènes de Bernard dans le hamac, devant le tableau, à la barrière et en récompense. Le texte est de la vraie interface Android, jamais une capture de maquette affichée comme application.

## Installer

Installer l’APK `Bernard-Bloqueur-v0.3.0.apk` sur Android 8 ou supérieur. Le paquet reste `com.local.focusfence`.

**APK de développement, pas une version Play Store.** L’ancienne V0.1.1 a été signée avec une autre clé de debug. Si Android refuse la mise à jour, désinstaller l’ancienne FocusFence avant d’installer cette version ; cela efface les anciens réglages et compteurs. Les builds suivants tentent de réutiliser une clé de debug en cache GitHub Actions, mais ce cache ne remplace pas une clé de publication conservée par le propriétaire.

Ouvrir Bernard, parcourir la présentation, puis activer l’accessibilité et l’accès aux données d’utilisation. L’interface est consultable sans ces accès, mais n’annonce pas une protection active quand ils manquent. Sur certains Android récents, les paramètres restreints doivent être autorisés dans la fiche de l’application avant l’accessibilité.

## Écrans et interactions

- Présentation en trois étapes et écran des autorisations.
- Accueil avec compteurs réels, temps restant, horaires et état de protection.
- Limites séparées de l’accueil : panneau compact, cartes de règles et applications individuelles.
- Éditeurs pleine page, quotas, plages nocturnes, option toute la journée, sélection indépendante des cinq sources de contenus courts.
- Sélection des jeux avec recherche et validation sans perdre le brouillon.
- Règles globales par application, y compris blocage permanent ; une telle règle bloque aussi les messages de cette application.
- Écrans de blocage illustrés réellement utilisés par le service, et aperçu accessible dans Paramètres.
- Collection de cinq images originales embarquées. Enregistrement d’une image débloquée via le sélecteur Android, sans permission générale de stockage.
- Historique local, journées non suivies clairement distinguées, export JSON via le sélecteur Android.
- Prénom modifiable, diagnostic local facultatif, crédits.

## Règles de calcul

`0 min` signifie **sans quota de durée**, pas un blocage permanent. Le blocage permanent possède son propre interrupteur. Les horaires sont des intervalles `[début, fin)` : à 22:00 une plage finissant à 22:00 est fermée. Début = fin signifie toute la journée. Les limites ne sont pas des mesures de dopamine.

Le temps global des apps est reconstitué à partir des événements d’activité Android et limité aux bornes du jour local, plutôt que de sommer aveuglément des agrégats journaliers. Le compteur Reels/Stories/Shorts utilise l’horloge monotone, compte aussi les vidéos regardées sans scroller et comptabilise la dernière fraction de seconde à la sortie.

## Récompenses honnêtes

Aucune récompense n’est accordée simplement en ouvrant l’application ou à midi. La journée courante est « en cours ». Les jours absents ne sont ni des victoires ni des échecs inventés. Le premier jour partiel, une interruption du service, des permissions manquantes ou une modification de règles empêchent de certifier cette journée. La validation intervient au changement de jour, avec une surveillance continue observée par le service et des objectifs finis actifs. Une très petite tolérance est prévue pour la latence de blocage des jeux. Les images obtenues restent débloquées même si la série se casse.

C’est une qualification locale et prudente, **pas une attestation infalsifiable**. Une fermeture brutale par le système peut invalider une journée. Une app personnelle sans contrôle administrateur ne peut pas empêcher la désactivation du service, la désinstallation, la suppression des données ou toutes les manipulations d’horloge.

## Vérification

JDK 17, AGP 8.7.3, Gradle Wrapper 8.9, SDK 35.

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Le workflow GitHub ajoute les tests instrumentés sur émulateur Android 15, des captures natives et deux scénarios de blocage avec une application de test : hors plage horaire et quota d’une minute. Le module `fixture` est **uniquement une app de test**, jamais intégré à l’APK de Bernard.

Les rapports et captures produits par la CI sont séparés de la source. Les données utilisées pour les captures sont injectées par les tests, pas dans l’application livrée.

## Limites restantes

Les détecteurs Instagram/Facebook/YouTube hérités sont des heuristiques d’accessibilité : leurs versions réelles, langues, interfaces et éventuels tests A/B doivent être vérifiés sur le téléphone de destination. Les messages ne sont pas intentionnellement ciblés, mais un Reel ouvert depuis un message peut être compté. Le navigateur web, les applis clonées, l’écran partagé et le picture-in-picture ne sont pas garantis. Pas de jours de semaine distincts ni de verrouillage anti-modification dans cette version. Le diagnostic n’enregistre que des identifiants techniques, pas le texte des messages.

Licence du code : GPL-3.0. Voir `LICENSE` et `THIRD_PARTY_NOTICES.md`.
