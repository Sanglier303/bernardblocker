# Protection GitHub de main

État constaté le 22 septembre 2026 : main n'est pas protégée. L'intégration utilisée pour cette intervention retourne HTTP 403 sur l'API d'administration des branches et n'expose pas d'action d'écriture de cette protection. Aucune activation n'est présumée.

Un administrateur peut appliquer la configuration après vérification des noms de checks affichés sur la PR :

```sh
gh api --method PUT repos/Sanglier303/bernardblocker/branches/main/protection --input tools/main-protection.json
gh api repos/Sanglier303/bernardblocker/branches/main/protection
```

La configuration impose une pull request et les quatre checks, une base à jour, les conversations résolues, et interdit force-push et suppression. Elle s'applique aussi aux administrateurs. Zéro approbation extérieure obligatoire évite de bloquer un dépôt maintenu par une seule personne ; cela ne supprime pas la PR obligatoire. Ne pas écraser une politique plus stricte préexistante sans la relire.

Référence officielle : https://docs.github.com/en/rest/branches/branch-protection#update-branch-protection
