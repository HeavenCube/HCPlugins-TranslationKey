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

**44 tests passés**, zéro échec, zéro ignoré, compilation `-Xlint:all` sans warning Java.
La première version avait 35 tests ; l'extension des balises en ajoute neuf.

Nouvelle exécution réussie avec `netty-buffer:4.2.18.Final`, changement proposé par la
[PR #1](https://github.com/HeavenCube/HCPlugins-TranslationKey/pull/1).
Cette dépendance reste limitée au runtime des tests ; le serveur fournit son propre Netty.

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
- Balises : les trois exemples value/colored_text, couleurs natives, refs/quotes/échappements/paramètres,
  allowlist PAPI, sons namespaced, effets et durées en ticks, bossbar en pourcentage.
- Pureté des balises : effets absents en PAPI/aperçu/référence interne, déduplication au dispatch,
  refus des effets injectés par argument et des définitions conflictuelles.
- Candidat inline invalide : graphe/budgets/valeurs refusés, snapshot antérieur conservé.
- Détection des feuilles : un groupe `components` avec un enfant `value` reste un groupe,
  tandis que `value`/`type` constitue une entrée typée.

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

Le joueur a confirmé que cette seconde navigation est plus claire.
Aucun test de charge spark/JFR n'a été exécuté. Aucun gain chiffré n'est revendiqué.
Les permissions refusées, un kick réel et la saturation réseau n'ont pas été testés sur le joueur ;
les tests automatisés ne sont pas présentés comme une observation visuelle de ces scénarios.

Les logs incluent volontairement une erreur de catalogue pendant le test de rollback.
L'avertissement d'alias venait de l'exemple actif `old.close-message`, désormais retiré des valeurs
par défaut : `aliases: {}`. Les véritables alias configurés restent signalés au chargement.
Le même exemple a été retiré du fichier actif sur le serveur ; reload réussi à 07:06:48,
sans avertissement d'alias. La clé `ui.close` et les autres contenus sont conservés.
Des erreurs de configuration d'autres plugins, notamment un modèle HeadBlocks/ModelEngine absent,
ont été observées au boot ; elles n'ont pas été modifiées dans cette tâche.

## Publication et installation finales — 5 octobre 2026

- Changement Netty de la PR #1 intégré directement à `main` dans `688b596` ; PR ensuite fermée,
  sans fusion manuelle. Netty reste exclusivement une dépendance de test.
- [CI du commit 688b596](https://github.com/HeavenCube/HCPlugins-TranslationKey/actions/runs/37266379850) : réussie.
- [Release v2](https://github.com/HeavenCube/HCPlugins-TranslationKey/releases/tag/v2) :
  version embarquée `2026.10.05-b2`, asset fixe `HCTranslationKey.jar`.
- Asset téléchargé depuis cette release, inspecté (Paper 26.3, aucune classe de dépendance embarquée),
  puis transféré vers `plugins/HCTranslationKey.jar`. SHA-256 local/distant identique :
  `5df692bb9e41c3a47cf4595e5d6f79b5d0e3209a09a15c1d81308ed7b6c5b00a`.
- Ancien JAR désactivé à 07:08:48 sans exception dans la séquence de désactivation.
  Activation à 07:09:20, 20 clés/alias, aucun avertissement HCTranslationKey.
- À 07:09:41 : `ver HCTranslationKey` confirme la release ; `validate` réussit ; `get ui.close`
  retourne `<red>Fermer</red>`. Aucun joueur connecté durant cette validation finale.
- La validation visuelle de la navigation repose sur la confirmation du joueur obtenue avant ce
  redémarrage ; cette dernière correction ne modifie pas les dialogues.
- Aucun code ni JAR de Core, PlaceholdersExtra ou des autres plugins modifié pour cette intégration.

## Validation des balises et de la configuration personnalisée

Le fichier actif a évolué depuis la première installation : charte personnalisée, icônes,
composants, alias réels et signaux sonores. Son contenu n'est pas remplacé par les defaults du dépôt.
Un candidat convertit uniquement `messages.no-permission`, `messages.reward` et les signaux sonores
vers la syntaxe inline, en conservant les clés, couleurs, textes, volumes, pitch et durées.

Comparaison Java pure sur les deux fichiers réels : **45 expressions** (arguments représentatifs `1`),
mêmes clés, mêmes composants Adventure et mêmes listes d'effets ; résolution pure sans effets.
Ce contrôle détecte aussi la régression possible du groupe `components.value`, désormais protégée par un test.
Il ne constitue pas une observation audio/visuelle dans un client Minecraft.

- [CI du correctif de groupes 1c96069](https://github.com/HeavenCube/HCPlugins-TranslationKey/actions/runs/37307874034) : réussie.
- [Release v4](https://github.com/HeavenCube/HCPlugins-TranslationKey/releases/tag/v4), version `2026.10.05-b4`.
- Asset de cette release inspecté puis déposé sous le nom fixe `plugins/HCTranslationKey.jar` ;
  SHA-256 local/distant identique : `3f3230685127991edd4cff25de5ebcf787d1c15420ac87d914c2997b16ef409e`.
- Trois remplacements ciblés dans le fichier actif : messages no-permission/reward et groupe signals.
  Nouvelle copie relue depuis le serveur : même comparaison réussie de 45 expressions.
- Le serveur était déjà arrêté avant ce déploiement. Son démarrage est en attente de confirmation ;
  le chargement et les effets côté client de cette nouvelle version ne sont pas encore vérifiés en jeu.
- La release v3 n'a pas été installée : la vérification du fichier personnalisé a révélé l'ambiguïté
  `components.value`, corrigée avant le déploiement v4.

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
