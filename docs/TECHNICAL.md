# Architecture technique

## Responsabilités

| Classe | Responsabilité |
| --- | --- |
| `HCTranslationKey` | Lifecycle, handles Core/PHE/PacketEvents, chargement asynchrone |
| `CatalogLoader` | YAML strict, types, graphe, arité, effets, constantes précompilées |
| `InlineTags` | Normalisation des balises de configuration vers références et specs existantes |
| `CatalogStore` | Publication volatile atomique ; rollback implicite si construction échoue |
| `Catalog` / `Entry` | Snapshot immuable et résolution |
| `Expression`, `Markers`, `Template` | Grammaire bornée et templates préparés au chargement |
| `PapiBridge` | Allowlist de placeholders sans effets ; accès externe sur thread serveur |
| `ComponentRenderer` | Remplacement des nœuds texte avec conservation des styles/événements |
| `PacketSurface` | Adaptation de wrappers PacketEvents 2.14.0, sans NMS |
| `PacketTranslator` | Interception ciblée, rendu direct ou différé, validation de session |
| `MainThreadQueue` | Handoff borné/event-driven, 512 entrées, 64 traitements par tour |
| `EffectDispatcher` | Sons, titres, actionbars, bossbars finies et nettoyage |
| `TranslationCommand`, `AdminDialog` | Administration native via Core et Paper |

Aucune API publique ni expansion PAPI supplémentaire.
Core reste propriétaire de `/hcplugins` et des traductions communes reload/permissions.
Le provider est enregistré via `HCPlaceholders.require(plugin).register(plugin, "tkey", provider)`.
Son handle est fermé au disable. Aucune modification du Core ni du routeur PlaceholdersExtra n'est nécessaire.

## Publication et threading

Le catalogue est préparé dans des maps locales, puis copié en structures immuables.
Les composants Adventure et les templates publiés sont immuables ; aucune lecture YAML au runtime.
Un traitement capture un catalogue une fois, y compris si le paquet attend dans la queue.

| Contexte | Travail autorisé |
| --- | --- |
| Enable | Copie initiale du fichier via Core, chargement borné, enregistrement des services |
| Worker virtuel unique | Lecture/compilation du candidat reload/validate ; aucune API Bukkit |
| Thread réseau | Filtrage des types, composants immuables, rendu sans PAPI externe |
| Appel PAPI hors main | Valeurs statiques/templates purs ; pas de délégation externe |
| Thread serveur | PAPI externe, joueurs, effets, publication du candidat, commandes/dialogues |

Un seul executor de configuration à thread virtuel est créé, sans timer ; fermé par `shutdownNow`.
Pas de virtual threads pour manipuler l'état Bukkit.
Les reloads simultanés sont refusés jusqu'à publication/rejet du candidat.
Le disable ferme la queue avant l'executor : aucun résultat tardif ne republie un catalogue.

## PacketEvents

Surfaces PLAY effectivement traitées :

- `SYSTEM_CHAT_MESSAGE` (message système et overlay/actionbar) ;
- `DISGUISED_CHAT` (chat non signé décoré) ;
- `ACTION_BAR` ;
- `SET_TITLE_TEXT`, `SET_TITLE_SUBTITLE` ;
- `BOSS_BAR` pour ADD/UPDATE_TITLE ;
- `PLAYER_LIST_HEADER_AND_FOOTER` pour les en-têtes/pieds TAB ;
- `DISCONNECT` pour le texte immédiat, sans effets.

`SET_TITLE_TIMES`, `CLEAR_TITLES` et les mises à jour/suppressions de bossbar suivent également la queue
lorsqu'une traduction attend, afin de conserver l'ordre des surfaces interceptées.
Les noms d'équipes/joueurs du TAB, corps du chat signé, paquets de connexion/configuration et inventaires
ne sont pas interceptés.

L'interception vérifie les marqueurs dans les composants texte/leurs enfants.
Sans marqueur ni travail différé, le wrapper préalable de l'événement est restauré :
aucun rendu, effet ou scheduler ; aucune réécriture supplémentaire de notre fait.
La lecture des composants des types sélectionnés reste nécessaire pour détecter un marqueur.

Un rendu statique réécrit le wrapper lié à l'événement. Les effets ne sont mis en file qu'après envoi.
Un rendu dynamique hors main thread annule l'original et conserve un **wrapper détaché** ; jamais de
`PacketSendEvent`, buffer Netty ou copie reference-counted entre threads.
La queue reprend le travail sur le thread serveur, revalide le joueur et l'identité du User PacketEvents,
puis envoie silencieusement le wrapper traduit. Le renvoi silencieux évite une seconde interception ;
les traitements normaux de priorité inférieure ont déjà eu lieu.

Pendant l'attente, les surfaces interceptées suivantes sont différées dans la même file FIFO.
Cela peut ajouter un ou plusieurs ticks sous saturation ; les autres types de paquets ne sont pas bloqués.
La queue est globale au plugin, bornée ; elle n'attend jamais un autre thread.
Si la file est pleine, l'original est conservé et un avertissement limité est émis.
À l'arrêt/au changement de session, les travaux devenus sans destinataire sont abandonnés.
Ne pas promettre une transaction globale avec des paquets non interceptés.

## Robustesse et limites

- Fichier 1 Mo maximum, profondeur YAML/références 32, ancres de collections YAML refusées.
- 4096 entrées et 4096 alias maximum.
- Expression 8192 caractères, 16 paramètres maximum.
- Sortie 32768 caractères, budget de résolution 1024 opérations.
- Un parcours de composants a également une limite de profondeur/travail/sortie.
- Les références du catalogue sont validées avant publication ; un budget runtime protège les arguments dynamiques.
- Warnings de rendu limités à un par dix secondes, sans cache de chaînes d'erreur à taille non bornée.
- Les erreurs réseau conservent le contenu initial ; elles n'envoient pas des effets partiels.
- PAPI externe : aucun appel à une expansion non explicitement autorisée, aucun second passage.
- Les marqueurs ne constituent **pas** une frontière d'autorisation : leur catalogue doit contenir des
  contenus publics, sans secret ; ne pas laisser un tiers injecter des marqueurs dans une surface sensible.
- `hcplugins.tkey.admin` est vérifiée pour commandes et callbacks, avec le contexte Paper habituel.

Le YAML utilise SafeConstructor/LoaderOptions de SnakeYAML fourni par Paper.
Cette lecture conserve les clés pointées et détecte les collisions lors de l'aplatissement,
au lieu de laisser ConfigurationSection normaliser silencieusement ces chemins.
Les helpers Core possèdent le chemin et la création initiale du fichier.

## Performances attendues, non benchmarkées

Les entrées statiques et leurs composants sont précompilés ; aucun cache de joueurs permanent.
Les templates de configuration sont préparés une fois.
Les balises inline sont extraites au chargement, puis publiées sous forme de specs typées.
`value`/`colored_text` rejoint le même pipeline que `text` ; aucun second interpréteur d'effets au runtime.
Un sous-titre seul utilise `sendTitlePart(SUBTITLE)` et les durées, sans effacer le titre actif.
Le catalogue réalise des lookups par hash, pas un scan de toutes les clés.
La recherche/liste admin est seule à parcourir les clés ; résultats bornés/paginés.
Les résultats PAPI externes sont réutilisés dans un même traitement, sans cache périmé entre traitements.
Il n'y a aucun polling de joueurs ni scan d'entités.

Ces bénéfices sont déduits du code et des tests. Aucun gain CPU/allocation spark/JFR n'est revendiqué.
En production, surveiller particulièrement les expansions PAPI tierces autorisées et la saturation de queue.

## Sources des contrats

- [Paper Dialog API](https://docs.papermc.io/paper/dev/dialogs/)
- [Javadocs Paper 26.3](https://jd.papermc.io/paper/26.3/)
- [MiniMessage](https://docs.papermc.io/adventure/minimessage/api/)
- [PlaceholderAPI](https://wiki.placeholderapi.com/developers/using-placeholderapi/)
- [PacketEvents 2.14.0](https://github.com/retrooper/packetevents/tree/v2.14.0)
- [Core : création de plugin](https://github.com/HeavenCube/HCPlugins-Core/blob/main/docs/NEW_PLUGIN.md)
- [PlaceholdersExtra](https://github.com/HeavenCube/HCPlugins-PlaceholdersExtra)
- [CI commune](https://github.com/HeavenCube/HCPlugins-actions)
