# HCTranslationKey

Catalogue français de textes et de composants visuels pour **HeavenCube**, sous **Paper/Minecraft 26.3 et Java 25**.

Couleurs, composants MiniMessage, templates, références, barres de progression et effets sont réunis dans
**`plugins/HCPlugins/HCTranslationKey.yml`**. Aucune migration automatique, aucune API publique Java,
aucun système multilingue. Implémentation indépendante ; aucun code ItsMyConfig repris.

Les effets peuvent être écrits directement dans le message :

```yaml
prefix: "<gold>HeavenCube</gold>"
erreur-divers:
  value: "<sound:block.chain.break:1:0.5><p:prefix> <c:#E62E39>{0}</c>"
  type: colored_text
```

La forme courte `erreur-divers: "<sound:...>..."` fonctionne aussi. `<p:...>` référence une clé,
`<papi:...>` un placeholder autorisé. Sons, actionbars, titres, sous-titres et bossbars ont une
[syntaxe en balises](docs/CONFIGURATION.md#messages-avec-balises).
Envoyer `[[hctkey:erreur-divers:Action impossible]]` déclenche le son ; le placeholder PAPI donne le texte sans effet.

## Deux usages distincts

| Usage | Syntaxe | Effets |
| --- | --- | --- |
| Configuration compatible PlaceholderAPI + MiniMessage | `%hcextra_tkey_ui.close%` | Jamais |
| Message/composant sortant pris en charge | `[[hctkey:messages.no-permission]]` | Ceux de la clé directement appelée |
| Template à deux arguments | `%hcextra_tkey_menu.title.item:Métiers:Pêcheur%` | Jamais |
| Référence et argument avec deux-points | `[[hctkey:menu.title.item:"Métiers : pêche":Pêcheur]]` | Effets directs uniquement |
| Barre de progression | `%hcextra_tkey_progress.experience:35:100%` | Jamais |
| Couleur brute | `%hcextra_tkey_theme.colors.primary%` | Jamais |

**MiniMessage exclusivement : aucune sortie legacy.** Le plugin consommateur doit traiter MiniMessage
après PlaceholderAPI. Les marqueurs PacketEvents conviennent aux messages compatibles ; les inventaires,
noms d'items et lores utilisent PlaceholderAPI, sans interception globale des ItemStacks.

HCPlaceholdersExtra possède toujours l'unique expansion `hcextra`. Ce plugin y enregistre le provider `tkey`.

## Installation

Installer ensemble :

- [HCCore](https://github.com/HeavenCube/HCPlugins-Core) ;
- [HCPlaceholdersExtra](https://github.com/HeavenCube/HCPlugins-PlaceholdersExtra) ;
- [PlaceholderAPI](https://github.com/PlaceholderAPI/PlaceholderAPI), version testée 2.12.3 ;
- [PacketEvents](https://github.com/retrooper/packetevents), version testée 2.14.0 ;
- [HCTranslationKey.jar — dernière release](https://github.com/HeavenCube/HCPlugins-TranslationKey/releases/latest/download/HCTranslationKey.jar).

Redémarrer Paper. Le fichier initial est créé uniquement s'il est absent.
Le JAR ne contient aucune copie de ces dépendances. Aucun changement dans les autres configurations n'est automatique.

## Administration

Permission : `hcplugins.tkey.admin`, accordée aux opérateurs par défaut.

- `/hcplugins tkey` : interface Paper pour un joueur ; aide pour la console.
- `reload` : valide un candidat puis le publie atomiquement ; en cas d'erreur, l'ancienne génération reste active.
- `validate` : vérifie le fichier sans changer le catalogue actif.
- `get <expression>` : affiche la source MiniMessage résolue et un aperçu **sans effets**.
- `send <expression> [joueur]` ou `test <expression> [joueur]` : texte et effets.
- `send <expression> --player <joueur>` : cible explicite, recommandée pour éviter une ambiguïté avec un argument.
- `search <texte>` et `list [préfixe]` : recherche et liste des clés.

Exemples :

```text
/hcplugins tkey get menu.title.item:"Métiers : pêche":Pêcheur
/hcplugins tkey test messages.reward:"100 pièces" --player Noltox
/hcplugins tkey get progress.summary:35:100
```

La lecture/validation au reload est asynchrone. Aucun accès Bukkit n'a lieu dans ce travail.
Les changements de thème sont visibles lors de la **prochaine résolution** par le plugin consommateur :
HCTranslationKey ne force pas le rafraîchissement de ses menus déjà ouverts.

## Documentation

- [Configuration, grammaire et exemples](docs/CONFIGURATION.md)
- [Architecture, limites et threading](docs/TECHNICAL.md)
- [Compilation, déploiement et validation](docs/VALIDATION.md)
- [Instructions pour les agents](AGENTS.md)
- [Licence HeavenCube](LICENSE)

## Développement et CI

Cloner côte à côte `HCPlugins-Core`, `HCPlugins-PlaceholdersExtra` et ce dépôt.
Sous Java 25 : `./gradlew build` ou `.\\gradlew.bat build` sur Windows.
Les deux APIs sont consommées en `compileOnly` par builds composites, sans publication Maven.

Le [workflow commun HCPlugins-actions](https://github.com/HeavenCube/HCPlugins-actions) est utilisé via `@main` :
release `vN`, version `AAAA.MM.JJ-bN`, asset fixe `HCTranslationKey.jar`, PR sans release.
Cache Gradle, `GRADLE_ENCRYPTION_KEY` et règles d'économie de CI suivent le workflow partagé.
Les changements uniquement documentaires n'exécutent pas de build ; utiliser `[skip ci]` pour leur commit.
