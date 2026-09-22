# Bernard 0.4.7 : durcissement et validation

Base auditée : `241176e8e746667d38311e934705f24e57701c3b`, arbre `5c7d67e104a67b603114e9bd39092e71f6e147b1` (0.4.6). Version préparée : 0.4.7, code 14. Les résultats doivent être lus sur le commit effectivement testé, pas déduits de ce document.

## Correctifs

Forteresse vérifie chaque postcondition : anti-désinstallation, autorisations d'accessibilité, protections des données, heure et fuseau, restrictions utilisateur compatibles avec la version Android. Chaque tentative est indépendante pour ne pas arrêter toute la séquence à la première erreur. Les suspensions vérifient la liste d'échecs retournée par Android ET l'état de chaque application. Un registre local conserve les applications à restaurer même après suppression d'une règle. Une récupération partielle ne peut plus afficher un succès complet. Les messages de support facultatifs ne sont pas des restrictions de sécurité.

Les nouvelles installations créent leur code privé à six chiffres, avec confirmation et sel aléatoire. Aucun code ni vérificateur universel n'est embarqué en production. Un ancien vérificateur demeure utilisable uniquement pour autoriser sa rotation : pas d'accès sensible avant création du nouveau code. Cette migration conserve règles et compteurs. L'ancien secret publié dans l'historique Git ne peut pas être rendu secret rétroactivement. La rotation nécessite nécessairement l'intervention du propriétaire sur l'appareil. Une perte de vérificateur dans une installation déjà provisionnée n'ouvre pas une nouvelle inscription. Une remise à zéro intégrale d'une installation ordinaire reste indiscernable d'une installation neuve ; les protections Device Owner sont nécessaires pour résister aux opérations système autorisées au propriétaire du téléphone.

Les règles d'application sont décodées intégralement avec contrôle des types, plages et doublons. Une corruption déclenche un verrou explicite et conserve une dernière copie valide. Si aucune copie exploitable n'existe, le mode de récupération refuse les applications non essentielles, sans empêcher Bernard, l'accueil et les fonctions téléphoniques essentielles. La réparation exige le PIN et une confirmation. Elle conserve les données de consommation et ne lève pas silencieusement le verrou. Un verrou partagé entre instances protège les transactions de règles.

Les exclusions de sauvegarde couvrent cloud et transfert appareil-vers-appareil, y compris les stockages protégés par l'appareil. Le manifeste conserve allowBackup=false et une règle compatible avant Android 12. Ces exclusions sont des instructions Android, pas une protection contre root ou une extraction forensique du téléphone.

La cible et la compilation passent à API 36 avec AGP 8.10.1, Gradle 8.11.1 et AndroidX Test 1.7.0/1.3.0. Les gestes de retour API 33+ utilisent explicitement le même refus/retour que le parcours historique. Les touches PIN rejettent les événements masqués dans le filtre de sécurité des vues. Les objets Path du dessin des icônes sont réutilisés.

## Reels et confidentialité

Aucun arbre réel provenant du téléphone de l'utilisateur n'a été fourni. Le corpus ajouté est explicitement SYNTHÉTIQUE et réutilise le classificateur de production. Il ne prouve pas toutes les variantes de Meta. La priorité DM/lecteur Reels et le mécanisme navigateur existants ne sont pas affaiblis pour obtenir des tests verts.

Le mode diagnostic, volontaire et désactivé par défaut, conserve au plus douze échantillons espacés de cinq secondes. Pour Instagram officiel seulement : noms techniques de ressources visibles/sélectionnées, état booléen de l'étiquette Reels sélectionnée et décision. Aucun texte d'écran, contenu de message, nom de compte, description libre ni URL n'est exporté. Désactiver le mode efface ces échantillons. Un nom de ressource Android peut identifier une variante de l'interface, pas le contenu de la conversation. Ces faits permettent de construire ensuite un vrai cas de régression avec l'accord du propriétaire.

Pour un nouvel incident : activer temporairement le diagnostic, reproduire, exporter depuis Bernard, désactiver ensuite. Relire l'export avant partage. Les navigateurs conservent leur dernier état social tant qu'aucune navigation lisible ne prouve le contraire : compromis anti-contournement non déclaré universellement résolu.

## Validation et incident API 36

L'ancien run main 35726718895 atteint trois tests réussis puis ne termine pas le parcours administrateur. Les journaux récupérés ne démontrent ni crash Java de Bernard ni une cause unique du blocage. La nouvelle instrumentation utilise une connexion UiAutomation cohérente qui ne supprime pas les services d'accessibilité, attend la déconnexion effective avant effacement des fixtures et exécute chaque méthode dans un processus séparé.

Chaque test a une limite de 120 secondes. Un watchdog indépendant capture les piles de tous les threads à 70 secondes sans dépendre du thread principal ni d'un arbre d'accessibilité. Le pilote collecte les preuves avant arrêt. Aucun échec n'est relancé pour être remplacé par un succès. Chaque méthode inventoriée doit avoir une terminaison positive, aucun test ignoré. Le parcours administrateur est exécuté trois fois sur API 36, toutes obligatoires.

Des tests Device Owner réels sont exécutés sur l'émulateur avec l'APK DEBUG testOnly : lecture d'un état incomplet, suspension/restauration après retrait de règle et refus partiel Android du launcher. Ils ne désactivent pas ADB et ne prétendent pas valider toutes les restrictions matérielles ou constructeur. testOnly, instrumentation et identité de test ne sont pas la signature de production.

La publication reste conditionnée au build, aux tests unitaires, aux deux Lint et aux trois versions Android. Une compilation seule n'est pas une livraison validée. Les invariants locaux et le harnais JVM ne remplacent pas Gradle, Android ou un essai réel de mise à jour.

## Limites d'autorisation

Le connecteur GitHub n'a pas accès à l'administration des branches (403 confirmé). `tools/main-protection.json` et `docs/MAIN-PROTECTION.md` préparent l'activation par l'administrateur. La présence de ces fichiers n'active pas la protection GitHub. Pas de prétention d'inviolabilité face au root, mode récupération, réinitialisation ou politiques constructeur. Les avertissements de présentation Lint résiduels sont à lire dans les rapports, sans suppression globale.

## Sources primaires

- DevicePolicyManager : https://developer.android.com/reference/android/app/admin/DevicePolicyManager
- Sauvegarde et D2D : https://developer.android.com/identity/data/autobackup
- Android 16 : https://developer.android.com/about/versions/16/behavior-changes-16
- AGP 8.10 : https://developer.android.com/build/releases/agp-8-10-0-release-notes
- AndroidX Test : https://developer.android.com/jetpack/androidx/releases/test
- Administration de main : https://docs.github.com/en/rest/branches/branch-protection#update-branch-protection
