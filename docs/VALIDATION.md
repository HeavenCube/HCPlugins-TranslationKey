# Compilation, déploiement et preuves

## Reproduire le build

Java 25, sans preview. Clones `HCPlugins-Core`, `HCPlugins-PlaceholdersExtra` et `HCPlugins-TranslationKey`
dans le même dossier parent ; en CI, clones sous `.hcplugins/`.

```powershell
$env:JAVA_HOME = 'C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot'
.\gradlew.bat build
```

Linux : `./gradlew build` avec JAVA_HOME pointant vers un JDK 25.
Ne pas réutiliser le chemin Windows sur une autre machine.

Résultat local : `build/libs/HCTranslationKey-<version>.jar`.
La CI conserve les conventions HCPlugins : tag vN, version datée et asset **HCTranslationKey.jar**.
Les deux APIs composites sont compileOnly. PacketEvents, PlaceholderAPI et Paper ne sont pas embarqués.

## Validation automatisée — 5 octobre 2026

**35 tests passés**, zéro échec, zéro ignoré, compilation `-Xlint:all` sans warning Java.

- Parser : quotes, échappement, deux-points, apostrophes, Unicode, espaces, vide, marqueurs imbriqués.
- Formulaire : conversion des champs en expression sans altérer quotes/deux-points.
- Catalogue : références et paramètres imbriqués, arité, aliases, cycles, inconnus.
- MiniMessage/PAPI : ordre de résolution, allowlist, refus des références récursives/expansions à effets.
- Arguments runtime : les accolades fournies comme valeur ne sont pas réinterprétées comme paramètres.
- Progression : ratio, bornes, maximum invalide, valeurs non finies.
- Configuration : défauts complets, YAML/doublons/types/champs/effets/durées invalides.
- Limites : profondeur, expansion exponentielle, sortie totale de plusieurs marqueurs.
- Snapshots : lecture concurrente et échec de reload conservant la génération antérieure.
- Queue : concurrence, ordre, coalescence, lots, saturation, fermeture, erreur de callback et rejet du scheduler.
- Composants : conservation des styles/événements, plusieurs références, identité inchangée sans marqueur.
- PacketEvents : encode/decode réels des wrappers sous protocole 26.3 avec buffers de test,
  métadonnées bossbar/titres, absence de référence de buffer dans le wrapper différé,
  exclusion inventaires et chat signé.

Les tests PacketEvents n'ouvrent pas de socket et ne démarrent pas Minecraft.
Ils ne remplacent pas les essais client décrits ci-dessous.

Le build complet HCCore a également réussi (tâches à jour). Son code est inchangé.
Le build de ce plugin compile les APIs Core/PlaceholdersExtra via les deux composites.
Le JAR a été inspecté : aucune copie des classes des dépendances serveur.

## Essais sur MineStrator 469150

Serveur observé : UniverseSpigot/Paper 26.3, Java 25, PacketEvents 2.14.0, PlaceholderAPI 2.12.3.
Premier JAR de développement chargé le 5 octobre 2026 à 00:38, version `2026.10.05-dev`.
Transfert SFTP vérifié par comparaison SHA-256 locale/distante.
Le fichier de configuration a été créé dans le dossier commun HCPlugins, sans modifier les autres plugins.

Vérifications effectuées :

- chargement des quatre dépendances puis HCTranslationKey, 21 clés/alias ;
- aide console, validate, get avec arguments quotés, search, progression ;
- `papi parse Noltiii` : couleur exacte, texte, template, référence imbriquée et `player_name` ;
- messages `tellraw` contenant un/deux marqueurs, template et PAPI dans une clé ;
- `test messages.reward` vers Noltiii : message et dispatch des effets ;
- ouverture de `/hcplugins tkey` par le joueur ;
- reload valide : `ui.close` devient temporairement « Fermer — test reload » ;
- reload volontairement invalide : référence inconnue refusée, ancienne valeur toujours retournée par PAPI ;
- restauration du fichier initial et reload : `ui.close` retourne de nouveau « Fermer » ;
- deuxième déploiement/redémarrage à 00:51 pour la réorganisation des dialogues ; démarrage normal.

Le joueur a confirmé le bon fonctionnement des textes/du plugin et signalé que la première interface
était peu intuitive. Elle a été remplacée par un accueil à trois choix, un catalogue par rubriques,
une pagination distincte, un formulaire de valeurs et un écran technique séparé.

La confirmation client de cette seconde interface est encore en cours lors de cette rédaction.
Aucun test de charge spark/JFR n'a été exécuté. Aucun gain chiffré n'est revendiqué.
Les permissions refusées, un kick réel et la saturation réseau n'ont pas été testés sur le joueur ;
les tests automatisés ne sont pas présentés comme une observation visuelle de ces scénarios.

Les logs incluent volontairement une erreur de catalogue pendant le test de rollback.
L'avertissement d'alias au chargement est attendu pour l'exemple `old.close-message`.
Des erreurs de configuration d'autres plugins, notamment un modèle HeadBlocks/ModelEngine absent,
ont été observées au boot ; elles n'ont pas été modifiées dans cette tâche.

## Scénario de recette réutilisable

1. Installer le JAR fixe avec ses dépendances, redémarrer et attendre l'état online.
2. Vérifier les logs, la version et l'emplacement du fichier.
3. Tester les placeholders sans effets :
   `papi parse <joueur> %hcextra_tkey_theme.colors.primary%`,
   `%hcextra_tkey_menu.title.item:Métiers:Pêcheur%`, `%hcextra_tkey_messages.welcome%`.
4. Ouvrir le dialogue, parcourir les rubriques, changer de page, rechercher, revenir à l'accueil.
5. Prévisualiser `messages.reward`, renseigner une valeur, puis envoyer l'essai.
6. Envoyer un `tellraw` avec deux fois `[[hctkey:messages.no-permission]]` : un seul effet sonore.
7. Envoyer un message sans marqueur ; vérifier qu'il reste inchangé.
8. Valider/recharger une modification, puis introduire une erreur contrôlée : ancien résultat préservé.
9. Restaurer la configuration valide et vérifier à nouveau la résolution.
10. Contrôler l'absence d'erreur au disable/restart ; vérifier qu'aucun second JAR n'est présent.

Ne jamais annoncer une recette client réussie sans observation/confirmation.
