# Configuration et expressions

## Catalogue

Fichier unique : `plugins/HCPlugins/HCTranslationKey.yml`. UTF-8, mappings et feuilles texte.
Les clés sont sensibles à la casse et respectent `[a-z][a-z0-9-]*(.[a-z0-9][a-z0-9-]*)*`.
Les groupes imbriqués produisent des identifiants à points ; les collisions entre clé pointée et groupe sont refusées.

`settings` et `aliases` sont réservés à la racine.
`theme.*` constitue un espace de tokens/groupes simples : il autorise notamment la couleur `theme.colors.text`.
En dehors de cet espace, les propriétés enrichies `text`, `sound`, `actionbar`, `bossbar`, `progress`
identifient une entrée enrichie. `title` est une propriété enrichie lorsqu'il contient les champs de titre.
Éviter ces noms comme sous-groupes ambigus. Les propriétés inconnues d'une entrée enrichie sont rejetées.

```yaml
theme:
  colors:
    primary: "#FBC62C"
components:
  prefix: "<[[hctkey:theme.colors.primary]]>HeavenCube</[[hctkey:theme.colors.primary]]>"
ui:
  close: "<red>Fermer</red>"
messages:
  welcome: "[[hctkey:components.prefix]] <gray>Bienvenue %player_name%.</gray>"
menu:
  title:
    item: "<gold>{0}</gold> <gray>— {1}</gray>"
```

MiniMessage strict : fermer les tags ouverts. Les tags natifs Adventure sont disponibles ;
les tags propriétaires d'ItsMyConfig (`<p:...>`, `<smallcaps>`, etc.) ne sont pas réimplémentés.
Un texte simple et les couleurs hexadécimales sont des valeurs valides.
Aucune sérialisation legacy, aucun format interne à base de `&`.

## Grammaire

```text
expression = identifiant ( ":" argument )*
marqueur   = "[[hctkey:" expression "]]"
paramètre  = "{" entier_de_0_à_15 "}"
```

- `:` sépare les arguments hors quotes, tags MiniMessage et marqueurs imbriqués.
- Quotes doubles ou simples en début d'argument ; les quotes fermantes terminent cet argument.
- Une apostrophe au milieu d'un argument est ordinaire : `L'ami`.
- `\:`, `\"`, `\'`, `\\` échappent séparateur, quote et antislash.
- Les espaces sont conservés. Argument vide : `clé::suivant` ou `clé:"":suivant`.
- Les autres antislashs sont conservés pour MiniMessage.
- Les marqueurs s'imbriquent, y compris dans un argument : `outer:[[hctkey:inner:valeur]]`.
- Les arguments MiniMessage doivent avoir des tags cohérents après substitution.
- Les paramètres sont contigus à partir de `{0}` ; nombre exact d'arguments, aucune valeur implicite.
- L'identifiant d'une référence est statique ; pas de construction de nom de clé par PAPI.

Exemples :

```text
menu.title.item:Métiers:Pêcheur
menu.title.item:"Métiers : pêche":Pêcheur
menu.title.item:Métiers:<gold>Pêcheur</gold>
menu.title.item:Métiers:[[hctkey:ui.close]]
```

Un marqueur envoyé par un autre plugin doit rester dans **un même nœud texte Adventure**.
Si ce plugin découpe le marqueur en composants en interprétant les tags de ses arguments avant l'envoi,
utiliser une référence à une clé déjà stylée, ou le placeholder PAPI résolu avant MiniMessage.
Les marqueurs dans les événements click/hover et clés de composants translatables ne sont pas exécutés.

## Pipeline

1. Parser l'expression et vérifier l'arité.
2. Résoudre les arguments et références HCTranslationKey depuis un snapshot unique.
3. Substituer les paramètres ; produire la barre de progression si présente.
4. Résoudre les placeholders externes autorisés, dans le contexte du destinataire.
5. Parser MiniMessage ; produire un composant Adventure.
6. Uniquement en dispatch : préparer les effets directs, dédupliquer, appliquer sur le thread serveur.

La sortie PAPI est la chaîne MiniMessage, à faire interpréter par le consommateur.
Une clé de couleur retourne simplement `#FBC62C`.
Les données retournées par PAPI externe sont échappées comme texte : elles ne créent ni tags ni nouveaux marqueurs.
Une donnée externe n'est pas relue récursivement comme une nouvelle expression.

### PAPI externe et pureté

```yaml
settings:
  allowed-placeholders:
    - player_name
    # Ajouter ici uniquement des identifiants exacts vérifiés comme lecture seule.
messages:
  welcome: "<gray>Bonjour %player_name%.</gray>"
```

L'allowlist interdit les commandes cachées dans des placeholders tiers.
Les placeholders connus give/remove de CheckItem et la récursion `hcextra_tkey_` sont interdits.
Autoriser une autre expansion exige de vérifier qu'elle ne réalise aucune action.
Le moteur ne peut pas rendre pure une extension tierce qui ne l'est pas.

PAPI utilise lui-même `%` comme délimiteur : ne pas imbriquer un placeholder `%...%` directement à
l'intérieur d'un autre `%hcextra_tkey_...%`. Mettre PAPI dans la **valeur du catalogue**.
Les commandes et marqueurs peuvent recevoir un placeholder PAPI comme argument.

Hors thread serveur, un appel PAPI statique fonctionne ; une résolution exigeant une expansion externe
est laissée non résolue, avec un avertissement limité. Le moteur n'attend jamais le scheduler depuis PAPI.
Les paquets dynamiques, eux, sont différés dans une file bornée et résolus sur le thread serveur.
Sans contexte joueur, une expansion peut conserver son placeholder.

## Effets

```yaml
messages:
  reward:
    text: "<green>Récompense : {0}</green>"
    sound:
      key: "minecraft:entity.player.levelup"
      source: master
      volume: 0.5
      pitch: 1.2
    actionbar: "<gold>{0}</gold>"
    title:
      title: "<gold>Félicitations</gold>"
      subtitle: "<yellow>{0}</yellow>"
      fade-in: "500ms"
      stay: "3s"
      fade-out: "500ms"
    bossbar:
      text: "<yellow>{0}</yellow>"
      color: yellow
      overlay: progress
      progress: 1.0
      duration: "3s"
```

- Son : identifiant namespaced syntaxiquement valide ; un son du resource pack reste permis.
- Volume `0..4`, pitch `0.01..2`, nombres finis.
- Durées entières avec `ms`, `s` ou `t` (50 ms) ; `0..300s`, bossbar strictement positive.
- Bossbar : couleur/overlay Adventure, progress `0..1`, une barre temporaire par joueur ;
  un nouvel effet barre remplace la précédente.
- `get`, l'inspecteur et PAPI ne déclenchent rien.
- `send/test` et les marqueurs de premier niveau dispatchent leurs effets.
- Un fragment inclus ne propage jamais ses effets.
- Deux effets égaux dans le même traitement sont exécutés une seule fois.
- La déconnexion résout le texte immédiatement sans effets : le client est en train de partir.

## Progression

```yaml
bars:
  experience:
    progress:
      length: 20
      completed: "<green>┃</green>"
      current: "<yellow>┃</yellow>"
      remaining: "<dark_gray>┃</dark_gray>"
      value: "{0}"
      maximum: "{1}"
  label: "[[hctkey:bars.experience:{0}:{1}]] <gray>{0}/{1}</gray>"
```

Appel : `bars.label:35:100`. Longueur entière `1..100`, maximum fini strictement positif.
Le ratio est borné entre 0 et 1. La cellule suivant les cellules terminées utilise `current`,
sauf à 100 % où toutes sont terminées. Chaque cellule peut contenir son propre symbole/style.
La valeur et le maximum acceptent références, paramètres et placeholders autorisés.
Aucune expression mathématique/script n'est évaluée. `text` et `progress` sont exclusifs dans une entrée.

## Alias et validation

```yaml
aliases:
  old.close-message: ui.close
```

Les cibles doivent exister ; collisions et cycles sont refusés.
Un avertissement récapitulatif borné est émis au chargement/reload, jamais à chaque lookup.

Le candidat est entièrement rejeté en cas de YAML invalide/doublon, type incorrect, référence inconnue,
cycle, nombre d'arguments incorrect, MiniMessage déséquilibré ou paramètre d'effet invalide.
Les valeurs dynamiques sont également vérifiées au runtime : le chargement ne peut pas prédire leur contenu.
