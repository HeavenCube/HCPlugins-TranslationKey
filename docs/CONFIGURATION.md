# Configuration et expressions

## Catalogue

Fichier unique : `plugins/HCPlugins/HCTranslationKey.yml`. UTF-8, mappings et feuilles texte.
Les clés sont sensibles à la casse et respectent `[a-z][a-z0-9-]*(.[a-z0-9][a-z0-9-]*)*`.
Les groupes imbriqués produisent des identifiants à points ; les collisions entre clé pointée et groupe sont refusées.

`settings` et `aliases` sont réservés à la racine.
`theme.*` constitue un espace de tokens/groupes simples : il autorise notamment la couleur `theme.colors.text`.
En dehors de cet espace, les propriétés enrichies `value`, `type`, `text`, `sound`, `actionbar`, `bossbar`, `progress`
identifient une entrée enrichie. `title` est une propriété enrichie lorsqu'il contient les champs de titre.
Éviter ces noms comme sous-groupes ambigus. Les propriétés inconnues d'une entrée enrichie sont rejetées.
Un groupe comportant un enfant nommé `value` reste un groupe s'il comporte d'autres enfants ordinaires ;
`type: colored_text` rend explicite une entrée typée. Une feuille `{value: "texte"}` suffit également.

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

MiniMessage strict : fermer les tags de style ouverts. Les tags natifs Adventure sont disponibles,
notamment `<c:#E62E39>texte</c>` (alias natif de couleur). Les balises de contenu ci-dessous sont autonomes.
Un texte simple et les couleurs hexadécimales sont des valeurs valides.
Aucune sérialisation legacy, aucun format interne à base de `&`.

## Messages avec balises

Syntaxe conseillée pour un message avec effets :

```yaml
prefix: "<gold>HeavenCube</gold>"
erreur-impossible:
  value: "<sound:block.chain.break:1.00:0.50><p:prefix> <c:#E62E39>Action impossible, {0}…</c>"
  type: colored_text
erreur-divers:
  value: "<sound:block.chain.break:1.00:0.50><p:prefix> <c:#E62E39>{0}</c>"
  type: colored_text
msg-reward:
  value: "<sound:block.trial_spawner.eject_item:1.00:1.00><p:prefix> <c:#CCCCCC>Tu viens de recevoir la récompense {0} !</c>"
  type: colored_text
```

`type: colored_text` est facultatif. Une chaîne simple fonctionne également :

```yaml
erreur-divers: "<sound:block.chain.break:1:0.5><p:prefix> <c:#E62E39>{0}</c>"
```

| Balise | Utilisation |
| --- | --- |
| `<p:clé[:arguments]>` | Contenu d'une clé du catalogue ; refs/paramètres/cycles validés, jamais ses effets |
| `<papi:player_name>` | Identifiant PAPI sans `%`, toujours soumis à `allowed-placeholders` |
| `<c:#E62E39>texte</c>` | Couleur MiniMessage native ; couleur nommée également acceptée |
| `<sound:block.chain.break[:volume:pitch]>` | Son, volume/pitch par défaut 1, source master |
| `<actionbar:"<gold>{0}</gold>">` | Texte au-dessus de la barre d'outils |
| `<title:"Titre"[:"Sous-titre"]>` | Titre, sous-titre facultatif |
| `<title:10:70:20:"Titre":"Sous-titre">` | Durées fade-in/stay/fade-out en ticks |
| `<subtitle:"Sous-titre">` ou `<subtitle:10:70:20:"Sous-titre">` | Mise à jour du sous-titre seul |
| `<bossbar:"Texte":50:RED:SOLID:100>` | Progression **0..100 %**, couleur, style et durée en ticks |

Un tick vaut 50 ms. Durées par défaut : titre 10/60/10 ticks, bossbar 60 ticks.
Styles bossbar : SOLID, NOTCHED_6, NOTCHED_10, NOTCHED_12, NOTCHED_20.
La version structurée `bossbar.progress` reste un ratio `0..1`.
Les animations de progression `progress`/`reverse`, annulation, delay/repeat et les transformations
smallcaps/quote/plain/uppercase/lowercase ne font pas partie de cette syntaxe.

Son : `block.chain.break`, `minecraft:block.chain.break` ou
`'heavencube:ui.click'` pour un son de pack. Une clé namespaced peut être quotée :
`<sound:'minecraft:block.chain.break':1:0.5>`. Les nombres du son sont fixes, validés au chargement.
Les anciens noms Bukkit en majuscules sont refusés avec un message explicite : utiliser la clé Minecraft
exacte pour préserver les underscores, par exemple `entity.experience_orb.pickup`.

Les arguments contenant `:` ou des balises se mettent entre quotes :
`<p:menu.title.item:"Métiers : pêche":Pêcheur>`. Les effets peuvent eux-mêmes contenir `{0}`,
`<p:...>` et `<papi:...>` dans leur **texte**, avec les mêmes contraintes que les messages.
Un effet n'est pas autorisé à l'intérieur du texte d'un autre effet.
Une entrée possède au plus un son, une actionbar, un titre **ou** sous-titre, et une bossbar.
Une définition inline et structurée du même effet est une erreur explicite.
`value` et `text` sont exclusifs ; les configs structurées existantes restent valides.

Les balises de configuration sont compilées au chargement : aucun accès YAML ou interpréteur
d'effets supplémentaire dans le traitement réseau. Le texte PAPI et `get` retire les effets,
sans les jouer. Pour envoyer texte + son, utiliser `[[hctkey:erreur-divers:Action impossible]]`
ou `/hcplugins tkey test erreur-divers:Action impossible --player Noltiii`.
Les balises brutes envoyées par un plugin tiers ne sont pas interceptées : elles appartiennent
aux **valeurs du catalogue**. Le marqueur hctkey reste le déclencheur explicite de traitement.

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

Par défaut, `aliases: {}` : aucun alias déprécié actif. L'exemple suivant est facultatif,
à utiliser uniquement pour maintenir une ancienne clé réellement employée.

```yaml
aliases:
  old.close-message: ui.close
```

Les cibles doivent exister ; collisions et cycles sont refusés.
Un avertissement récapitulatif borné est émis au chargement/reload, jamais à chaque lookup.

Le candidat est entièrement rejeté en cas de YAML invalide/doublon, type incorrect, référence inconnue,
cycle, nombre d'arguments incorrect, MiniMessage déséquilibré ou paramètre d'effet invalide.
Les valeurs dynamiques sont également vérifiées au runtime : le chargement ne peut pas prédire leur contenu.
