# Bernard 0.4.5 : plages horaires et blocages obsolètes

Base contrôlée : 0.4.4, commit 38b335551b225851af6865c47fb39ef36fb22cac.
Version de correction : 0.4.5, versionCode 12. La clé de signature, le PIN et les données existantes restent inchangés.

## Défauts confirmés dans la base

1. Dans FocusAccessibilityService.sample(), `if(overlay!=null)return` empêchait toute réévaluation une fois un écran de blocage affiché. L'écran pouvait survivre à une modification de règle, une ouverture de plage ou un nouveau jour. Le service relit maintenant toutes les règles qui concernent le package et la surface mémorisés, même pendant le blocage. Il retire un blocage devenu injustifié, ou actualise son motif sans répéter la navigation Android. Une autorisation PIN, un quota épuisé ou une autre règle restrictive ne sont pas supprimés à cette occasion.
2. Les changements de préférences ne déclenchaient pas directement d'évaluation. Un listener borné aux clés de règles demande désormais un nouveau contrôle. Les écritures du journal et du diagnostic ne le déclenchent pas. Le polling reste présent pour les changements naturels d'heure/date sans interaction.
3. L'éditeur déduisait « Toute la journée » de l'égalité des deux heures pendant la saisie. Déplacer provisoirement un début sur l'ancienne fin cachait les champs et changeait implicitement le mode. Ce mode est maintenant explicite dans le brouillon; une plage manuelle de durée nulle est refusée. Le basculement temporaire journée entière / plage restaure les heures du brouillon, au lieu de les remplacer par les valeurs par défaut.
4. Une durée vide, illisible ou trop grande pour Integer.parseInt devenait 0, donc illimitée. L'éditeur refuse désormais la sauvegarde hors 0..1440; seul un 0 saisi explicitement désactive le quota. Une saisie invalide ne sauvegarde pas les horaires partiellement. Les groupes sont enregistrés en une transaction de préférences, sans toucher aux compteurs.
5. Le diagnostic « Compteur actif » était basé sur la sélection d'une source, même si elle venait d'être bloquée. Il reflète maintenant le comptage effectif et s'éteint au départ de la surface.
6. Le motif horaire était examiné avant l'épuisement du quota. L'écran pouvait annoncer une ouverture dans la journée alors qu'aucun temps n'y serait disponible. Un quota épuisé est désormais prioritaire; les détails n'annoncent pas une autorisation globale en ignorant les autres règles.
7. L'ouverture de l'interface Bernard ne pouvait pas toujours retirer un overlay déjà présent. Son retour au premier plan retire uniquement l'overlay visuel, pas le PIN, les restrictions ou le service. Revenir dans une application toujours interdite la bloque de nouveau.

## Calcul des horaires et comportement attendu

Rules.allowed était déjà correct : début inclus, fin exclue, plages traversant minuit prises en charge, début=fin représentant une journée entière dans le stockage. L'égalité reste rétrocompatible pour les règles existantes; elle n'est plus activée implicitement pendant la saisie d'une plage manuelle.

Le quota est quotidien, partagé entre les sources sociales sélectionnées. Modifier les horaires ne le remet pas à zéro. Du temps disponible n'autorise pas l'utilisation hors plage, contre une règle d'application entière, sans une permission nécessaire ou avec un verrou anti-contournement actif. Le nouveau AccessEvaluator partage la décision entre le premier blocage, la réévaluation de l'overlay et le contrôle différé de redirection Instagram. Il conserve la restriction la plus forte parmi les règles simultanées.

Les règles d'application entière restent volontaires et peuvent aussi bloquer les messages privés d'Instagram. Elles ne doivent pas être confondues avec la protection sélective du scroll. Un rappel est ajouté à l'accueil lorsqu'il existe des règles d'application.

## Tests ajoutés

- Tests purs couvrant toutes les minutes de cinq types de plages, ouverture/fermeture, passage à minuit, changement de plage sans réinitialiser le quota et priorité des restrictions.
- Validation des durées vides, trop grandes, négatives ou non numériques; 0 explicite et journée entière explicite.
- Tests Android du vrai service et de ses overlays : réouverture avec temps disponible, fermeture immédiate d'une plage, règle individuelle supplémentaire, maintien du verrou anti-contournement et retour dans Bernard.
- Test social sur l'application de fixture, dont la surface est injectée par réflexion dans le détecteur uniquement dans l'APK de test. Vérification que le blocage horaire ne consomme ni ne réinitialise les trois minutes enregistrées, puis que le comptage reprend après réouverture.
- Tests de l'éditeur et du bouton Enregistrer : heures égales manuelles refusées, conservation d'une plage personnalisée, durée invalide empêchant toute sauvegarde partielle et transaction de groupe cohérente.
- La CI exécute les parcours sur les API 26, 35 et 36 (Android 8, 15 et 16), en conservant Lint debug/release, signature permanente et publication seulement après réussite de toute la matrice.

Lire les rapports du run du commit effectivement livré pour les résultats. Les tests de changement d'heure simulée sont des tests de décision; ils ne manipulent pas l'horloge Android pour masquer la protection anti-contournement.

## Diagnostic et limites

L'export ajoute `lastObservedBlock` : date de l'événement, package, surface reconnue, motif, règle, temps utilisé, quota et plage. Il s'agit du dernier blocage observé, pas d'une affirmation que le blocage est encore actif. Aucun texte des messages privés ni PIN n'y est enregistré.

Sans le diagnostic de l'incident ancien, on ne peut pas déterminer sa cause sur le Pixel physique. L'overlay obsolète est une cause démontrable dans le code de 0.4.4, mais Instagram officiel utilise une redirection sélective plutôt que cet overlay pour le seul quota social. Le code de redirection est revérifié avec la décision commune; les variantes réelles d'Instagram ne sont pas toutes reproduites par les fixtures.

Un changement de plage dans Bernard ne modifie pas l'horloge système et ne crée pas en lui-même de verrou anti-contournement. En revanche, l'application reste volontairement conservatrice lors d'un véritable changement d'heure/fuseau Android, y compris lorsqu'elle ne peut pas distinguer avec certitude une correction automatique d'une intervention manuelle. Le verrou peut alors exiger une vérification PIN; ce comportement n'est pas désactivé par ce correctif. Device Owner complet, récupération privilégiée et toutes les variantes de clients sociaux restent hors preuve exhaustive.

## Références

- Code avant correction : app/src/main/java/com/local/focusfence/service/FocusAccessibilityService.java et ui/MainActivity.java au commit de base.
- Android SharedPreferences : https://developer.android.com/reference/android/content/SharedPreferences
- Android AccessibilityService : https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
- Android Intent (ACTION_TIME_CHANGED / ACTION_TIMEZONE_CHANGED) : https://developer.android.com/reference/android/content/Intent
- Niveaux API : https://developer.android.com/tools/releases/platforms
