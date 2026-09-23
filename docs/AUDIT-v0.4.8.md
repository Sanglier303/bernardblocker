# Bernard 0.4.8 — ADB autorisé comme usage propriétaire

## Incident reproduit par diagnostic

Le diagnostic 0.4.7 montrait un quota social intact mais des décisions de blocage `TAMPER` causées par l'ancien contrôle `Settings.Global.ADB_ENABLED`. Cette règle confondait l'activation volontaire d'ADB par le propriétaire avec une tentative de contournement.

## Changement de politique

ADB est maintenant explicitement autorisé. `PermissionUtils.isAdbEnabled()` est conservé uniquement pour l'export diagnostic local, afin de pouvoir expliquer l'environnement du téléphone. Il n'est plus consulté par le service d'accessibilité ni par le parcours de réactivation.

Les autres protections restent inchangées : changements d'heure/fuseau, raccourci d'accessibilité Bernard, désactivation du service, permissions d'utilisation requises, intégrité des règles, Device Admin/Forteresse et protections des écrans système.

## Migration 0.4.7 → 0.4.8

Un téléphone déjà verrouillé par l'ancien motif exact `Le débogage ADB est actif et peut contourner Bernard` retire automatiquement ce verrou lors de la création de `Prefs`.

La migration est volontairement étroite : elle ne supprime aucun autre `tamperLock`. Si une autre condition de protection est réellement invalide, le service la détecte normalement et conserve/réinstalle son propre motif.

## Validation ajoutée

- test Android : l'ancien verrou ADB est retiré ;
- test Android : un verrou d'un autre motif reste intact ;
- test Android : ADB apparaît dans le diagnostic comme information ;
- invariant CI : aucune classe de production autre que `PermissionUtils` et `DiagnosticReport` ne peut appeler `isAdbEnabled()` ;
- invariant CI : les anciens messages bloquants ADB ne doivent plus exister dans le service ou le parcours de réactivation.

La validation complète doit être lue dans le workflow du commit final : JUnit, Lint debug/release, compilation et parcours natifs API 26/35/36. Une branche verte ne constitue pas à elle seule une validation du téléphone réel ; la mise à jour sur le Pixel doit encore confirmer que l'ancien verrou est bien migré et que les Reels utilisent ensuite uniquement le quota et les autres règles configurées.
