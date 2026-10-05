# Instructions — HCPlugins-TranslationKey

## Lecture ciblée

1. Lire ce fichier, `git status --short`, puis la section utile de `docs/TECHNICAL.md`.
2. Avant un changement technique, lire `../HCPlugins-Core/AGENTS.md` et l'API Core utilisée.
   En CI, les clones composites sont dans `.hcplugins/`.
3. Intégration placeholders : inspecter `../HCPlugins-PlaceholdersExtra/placeholders-api` ;
   grammaire/configuration : `docs/CONFIGURATION.md` ; preuves de test : `docs/VALIDATION.md`.
4. Rechercher avec rg dans les sources/tests ciblés. Ne pas charger tous les dépôts, caches ou logs.

## Invariants

- Java 25 sans preview ; Paper 26.3 uniquement, `26.3.build.+`. APIs publiques Paper/Adventure.
- Aucun NMS, CraftBukkit, Unsafe, réflexion de production ou framework supplémentaire.
- Aucun code repris d'ItsMyConfig. Licence HeavenCube.
- **MiniMessage uniquement. Ne pas ajouter de sortie legacy.**
- HCCore, HCPlaceholdersExtra, PlaceholderAPI et PacketEvents sont obligatoires.
- Core et placeholders-api en compileOnly/composite ; jamais embarqués, relocalisés ou publiés Maven ici.
- Aucune API publique HCTranslationKey. HCCore ne dépend pas de ce plugin.
- Une seule expansion HeavenCube : provider `tkey` dans le registre `hcextra`.
- PAPI est toujours pur. Ne jamais dispatch des effets depuis resolve().
- PAPI externe : allowlist de valeurs sans effets ; pas de résolution récursive ; jamais sur Netty.
- Effets uniquement des clés de premier niveau, dédupliqués par traitement ; références internes pures.
- Balises inline et value/colored_text compilés au chargement vers les mêmes specs ; jamais d'effets
  interprétés depuis un argument/PAPI ni de seconde chaîne de dispatch. Tester références, arité et rollback.
- Config unique `plugins/HCPlugins/HCTranslationKey.yml`, aucun système de migration.
- Catalogue immuable, publication atomique après validation complète. Ancien snapshot préservé à l'échec.
- Limites de taille/profondeur/travail obligatoires. Ne pas remplacer le parseur par split(":").
- PacketEvents : wrappers détachés entre threads, jamais de buffer/event Netty retenu.
  Une écriture directe réutilise un wrapper lié à l'événement ; tester son encode/decode réel.
- Ne pas modifier les corps signés du chat ni parcourir tous les paquets d'inventaire.
- Thread serveur pour joueurs/effets/registre/commands. Queue bornée, sans attente bloquante ni polling.
- Le worker de configuration appartient au plugin et s'arrête au disable.
- Fermer abonnements, handles, tâches et bossbars ; revalider session/permission dans les callbacks.
- Les fonctionnalités réellement communes sont à évaluer dans Core ; pas d'abstraction artificielle.

## Validation et Git

- Compiler/tester avec Java 25 : `./gradlew build` ou `.\\gradlew.bat build`.
- Parser, catalogue, rollback, concurrence et encode/decode PacketEvents ont des tests ciblés.
- Une API Core/placeholders modifiée impose de reconstruire ses consommateurs.
- Documentation seule : vérifier faits/liens et `git diff --check`, sans build/release manuel.
- Préserver CI partagée @main, version date-build, tags vN, JAR fixe et cache/encryption.
- Commits en français. Respecter l'autorisation Git donnée dans la session ; ne pas redemander une
  permission déjà accordée. Sans autorisation, pas de publication, reset/rebase/stash/changement de branche.
- Préserver le travail d'autres intervenants ; ne jamais déployer/redémarrer sans autorisation de session.
- Distinguer tests automatisés, tests serveur et observations du joueur. Ne pas inventer de mesure spark.

## Efficacité et restitution

- Lectures ciblées, appels indépendants regroupés, sorties limitées aux preuves utiles.
- Pas de sous-agents sans instruction explicite. Pas de refactoring cosmétique.
- Continuer les tâches autorisées ; ne demander que les informations réellement bloquantes.
- Réponse française concise : modifications, validation réelle, limites et prochaine action.
- Handoff : état Git, fichiers touchés, tests, déploiement et travail restant ; aucun secret.
