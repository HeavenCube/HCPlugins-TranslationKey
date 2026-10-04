package fr.noltox.hcplugins.translationkey;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.*;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import java.time.Duration;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/** Separate home, catalog, preview and maintenance screens. */
final class AdminDialog {
    private static final ClickCallback.Options OPTIONS = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(2)).build();
    private static final int PAGE_SIZE = 6;
    private final HCTranslationKey plugin;
    AdminDialog(HCTranslationKey plugin) { this.plugin = plugin; }

    void open(Player player) {
        show(player, "Contenus HeavenCube",
                List.of(body("Retrouvez les textes et les couleurs du serveur, puis prévisualisez-les avant de les utiliser."),
                        body(plugin.catalog().keys().size() + " contenus disponibles • MiniMessage")),
                List.of(), List.of(
                        button(player, "Parcourir les contenus", NamedTextColor.GOLD, "Textes et couleurs classés par rubrique.",
                                (p, values) -> categories(p)),
                        button(player, "Tester un contenu", NamedTextColor.GREEN, "Choisir un contenu et renseigner ses valeurs.",
                                (p, values) -> choose(p, "")),
                        button(player, "Configuration", NamedTextColor.GRAY, "Vérifier et appliquer les modifications du fichier.",
                                (p, values) -> configuration(p))),
                close(player), 1);
    }

    private void categories(Player player) {
        var groups = new TreeMap<String, Integer>();
        for (String key : plugin.catalog().keys()) groups.merge(group(key), 1, Integer::sum);
        var actions = new ArrayList<ActionButton>();
        actions.add(button(player, "Rechercher dans tous les contenus", NamedTextColor.GOLD,
                "Saisir une partie d'identifiant dans le champ ci-dessus.",
                (p, values) -> list(p, "", value(values, "value"), 0)));
        for (var entry : groups.entrySet()) {
            String group = entry.getKey();
            actions.add(button(player, groupLabel(group) + " · " + entry.getValue(), NamedTextColor.WHITE,
                    "Identifiants commençant par " + group + ".",
                    (p, values) -> list(p, group, "", 0)));
        }
        show(player, "Catalogue — rubriques", List.of(body("Choisissez une rubrique, ou recherchez un identifiant.")),
                List.of(input("value", "Rechercher un contenu", "")), actions, home(player), 1);
    }

    private void list(Player player, String group, String search, int page) {
        String filter = search.toLowerCase(Locale.ROOT);
        var keys = plugin.catalog().keys().stream()
                .filter(k -> group.isEmpty() || group(k).equals(group)).filter(k -> k.contains(filter)).toList();
        int pages = Math.max(1, (keys.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.clamp(page, 0, pages - 1);
        var actions = new ArrayList<ActionButton>();
        actions.add(button(player, "Appliquer la recherche", NamedTextColor.GOLD, "Filtrer cette rubrique.",
                (p, values) -> list(p, group, value(values, "value"), 0)));
        actions.add(button(player, "Effacer la recherche", NamedTextColor.GRAY, "Afficher toute la rubrique.",
                (p, values) -> list(p, group, "", 0)));
        int first = current * PAGE_SIZE, last = Math.min(first + PAGE_SIZE, keys.size());
        for (String key : keys.subList(first, last)) {
            String canonical = plugin.catalog().aliases().get(key);
            actions.add(button(player, key, NamedTextColor.WHITE,
                    canonical == null ? "Voir l'aperçu et préparer un essai." : "Ancien nom de " + canonical,
                    (p, values) -> inspect(p, key, List.of(), currentPlayer -> list(currentPlayer, group, search, current))));
        }
        if ((last - first) % 2 == 1) actions.add(disabled("Fin de la liste"));
        actions.add(current == 0 ? disabled("◀ Page précédente")
                : button(player, "◀ Page précédente", NamedTextColor.AQUA, "Page " + current + " sur " + pages,
                        (p, values) -> list(p, group, search, current - 1)));
        actions.add(current + 1 == pages ? disabled("Page suivante ▶")
                : button(player, "Page suivante ▶", NamedTextColor.AQUA, "Page " + (current + 2) + " sur " + pages,
                        (p, values) -> list(p, group, search, current + 1)));
        show(player, group.isEmpty() ? "Résultats de recherche" : groupLabel(group), List.of(
                DialogBody.plainMessage(Component.text("Page " + (current + 1) + " / " + pages, NamedTextColor.AQUA).decorate(TextDecoration.BOLD)),
                body(keys.isEmpty() ? "Aucun contenu trouvé. Essayez une autre recherche." : keys.size() + " résultat(s). Cliquez sur un identifiant pour voir son aperçu.")),
                List.of(input("value", "Rechercher dans cette liste", search)), actions,
                button(player, "↩ Retour aux rubriques", NamedTextColor.YELLOW, "", (p, values) -> categories(p)), 2);
    }

    private void choose(Player player, String initial) {
        show(player, "Choisir un contenu",
                List.of(body("Saisissez son identifiant, par exemple messages.reward. L'écran suivant vous demandera les valeurs nécessaires.")),
                List.of(input("value", "Identifiant du contenu", initial)),
                List.of(button(player, "Préparer l'aperçu", NamedTextColor.GOLD, "Aucun son ou effet n'est joué à cette étape.",
                        (p, values) -> {
                            Expression expr = Expression.parse(value(values, "value"));
                            inspect(p, expr.key(), expr.arguments(), currentPlayer -> choose(currentPlayer, expr.key()));
                        })), home(player), 1);
    }

    private void inspect(Player player, String key, List<String> arguments, Consumer<Player> back) {
        Catalog c = plugin.catalog();
        Entry entry = c.entry(key);
        var body = new ArrayList<DialogBody>();
        body.add(body("Identifiant : " + key));
        var inputs = new ArrayList<DialogInput>();
        for (int i = 0; i < entry.arity(); i++) inputs.add(input("arg" + i,
                "Valeur " + (i + 1) + " — remplace {" + i + "}", i < arguments.size() ? arguments.get(i) : ""));
        if (entry.arity() > 0) body.add(body("Renseignez les " + entry.arity() + " valeur(s), puis cliquez sur « Actualiser l'aperçu »."));
        body.add(DialogBody.plainMessage(Component.text("APERÇU — sans effets", NamedTextColor.GOLD).decorate(TextDecoration.BOLD)));
        if (arguments.size() == entry.arity()) {
            try {
                var rendered = c.render(expression(key, arguments), PapiBridge.context(player, c.allowedPlaceholders()), false);
                body.add(DialogBody.plainMessage(rendered.component(), 400));
            } catch (IllegalArgumentException ex) { body.add(body("À corriger : " + ex.getMessage())); }
        } else body.add(body("L'aperçu apparaîtra après avoir renseigné les valeurs."));
        String effects = effectNames(entry.effects());
        body.add(body(effects.isEmpty() ? "L'essai envoie uniquement le texte dans le chat."
                : "L'essai envoie le texte et déclenche : " + effects + "."));
        var actions = List.of(
                button(player, "Actualiser l'aperçu", NamedTextColor.GOLD, "Afficher les valeurs saisies, sans aucun effet.",
                        (p, values) -> inspect(p, key, arguments(values, entry.arity()), back)),
                button(player, "Envoyer l'essai", NamedTextColor.GREEN, "Fermer cet écran et envoyer le contenu à vous-même.",
                        (p, values) -> {
                            Catalog snapshot = plugin.catalog();
                            var result = snapshot.render(expression(key, arguments(values, entry.arity())),
                                    PapiBridge.context(p, snapshot.allowedPlaceholders()), true);
                            p.closeDialog(); p.sendMessage(result.component()); plugin.effects().dispatch(p, result.effects());
                        }),
                button(player, "Voir la définition technique", NamedTextColor.GRAY, "Source MiniMessage, références et paramètres.",
                        (p, values) -> definition(p, key, arguments(values, entry.arity()), back)));
        show(player, "Aperçu du contenu", body, inputs, actions,
                button(player, "↩ Retour", NamedTextColor.YELLOW, "", (p, values) -> back.accept(p)), 1);
    }

    private void definition(Player player, String key, List<String> args, Consumer<Player> back) {
        Entry e = plugin.catalog().entry(key);
        String source = e.progress() == null ? e.text().source() : e.progress().toString();
        if (source.length() > 2000) source = source.substring(0, 2000) + "…";
        show(player, "Définition technique", List.of(body("Identifiant : " + key + "\nClé réelle : " + e.key()
                        + "\nValeurs requises : " + e.arity() + "\nRéférences : " + e.references()
                        + "\nEffets : " + effectNames(e.effects())), body(source)),
                List.of(), List.of(), button(player, "↩ Retour à l'aperçu", NamedTextColor.YELLOW, "",
                        (p, values) -> inspect(p, key, args, back)), 1);
    }

    private void configuration(Player player) {
        show(player, "Configuration",
                List.of(body("Fichier : plugins/HCPlugins/HCTranslationKey.yml"),
                        body("Après avoir modifié le fichier, vérifiez-le puis appliquez les changements. Un fichier invalide ne remplace jamais la version active.")),
                List.of(), List.of(
                        button(player, "Vérifier le fichier", NamedTextColor.GOLD, "Rechercher les erreurs, sans appliquer de changement.",
                                (p, values) -> { p.closeDialog(); plugin.load(p, false); }),
                        button(player, "Appliquer les changements", NamedTextColor.GREEN, "Vérifier puis recharger. Le résultat sera affiché dans le chat.",
                                (p, values) -> { p.closeDialog(); plugin.load(p, true); })), home(player), 1);
    }

    static String expression(String key, List<String> arguments) {
        var out = new StringBuilder(key);
        for (String arg : arguments) out.append(":\"").append(arg.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        return out.toString();
    }
    private static List<String> arguments(Function<String, String> values, int count) {
        var result = new ArrayList<String>();
        for (int i = 0; i < count; i++) result.add(value(values, "arg" + i));
        return List.copyOf(result);
    }
    private static String value(Function<String, String> values, String key) { return Objects.requireNonNullElse(values.apply(key), ""); }
    private static String group(String key) { int dot = key.indexOf('.'); return dot < 0 ? key : key.substring(0, dot); }
    private static String groupLabel(String group) {
        return switch (group) {
            case "theme" -> "Couleurs et style";
            case "components" -> "Éléments réutilisables";
            case "messages" -> "Messages";
            case "menu" -> "Textes des menus";
            case "ui" -> "Boutons et navigation";
            case "progress" -> "Barres de progression";
            case "old" -> "Anciens identifiants";
            default -> group;
        };
    }
    private static String effectNames(Effects.Spec e) {
        var names = new ArrayList<String>();
        if (e.sound() != null) names.add("son");
        if (e.actionbar() != null) names.add("texte au-dessus de la barre d'outils");
        if (e.title() != null) names.add("titre à l'écran");
        if (e.bossbar() != null) names.add("barre temporaire en haut de l'écran");
        return String.join(", ", names);
    }
    private static DialogBody body(String text) { return DialogBody.plainMessage(Component.text(text), 400); }
    private static DialogInput input(String key, String label, String initial) {
        return DialogInput.text(key, Component.text(label)).initial(initial).maxLength(1024).width(400).build();
    }
    private static ActionButton disabled(String label) { return ActionButton.create(Component.text(label, NamedTextColor.DARK_GRAY), null, 260, null); }
    private ActionButton close(Player player) {
        return button(player, "Fermer", NamedTextColor.GRAY, "", (p, values) -> p.closeDialog());
    }
    private ActionButton home(Player player) {
        return button(player, "↩ Retour à l'accueil", NamedTextColor.YELLOW, "", (p, values) -> open(p));
    }
    private ActionButton button(Player owner, String label, NamedTextColor color, String tooltip,
                                BiConsumer<Player, Function<String, String>> action) {
        UUID id = owner.getUniqueId();
        return ActionButton.create(Component.text(label, color).decorate(TextDecoration.BOLD),
                tooltip.isEmpty() ? null : Component.text(tooltip), 260, DialogAction.customClick((response, audience) -> {
            var inputs = new HashMap<String, String>();
            inputs.put("value", Objects.requireNonNullElse(response.getText("value"), ""));
            for (int i = 0; i < Limits.ARGUMENTS; i++) inputs.put("arg" + i, Objects.requireNonNullElse(response.getText("arg" + i), ""));
            if (!plugin.active()) return;
            plugin.handoff().submit(() -> {
                Player current = plugin.getServer().getPlayer(id);
                if (!plugin.active() || current == null || current != audience || !current.isOnline()
                        || !current.hasPermission(HCTranslationKey.PERMISSION)) return;
                try { action.accept(current, inputs::get); }
                catch (IllegalArgumentException | IllegalStateException ex) {
                    show(current, "Contenu à vérifier", List.of(body(ex.getMessage())), List.of(), List.of(),
                            button(current, "↩ Choisir un contenu", NamedTextColor.YELLOW, "",
                                    (p, values) -> choose(p, inputs.get("value"))), 1);
                }
            });
        }, OPTIONS));
    }
    private void show(Player player, String title, List<DialogBody> body, List<DialogInput> inputs,
                      List<ActionButton> actions, ActionButton exit, int columns) {
        if (!plugin.active() || !player.isOnline() || !player.hasPermission(HCTranslationKey.PERMISSION)) return;
        player.showDialog(Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text(title, NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
                        .canCloseWithEscape(true).pause(false).afterAction(DialogBase.DialogAfterAction.NONE)
                        .body(body).inputs(inputs).build())
                .type(actions.isEmpty() ? DialogType.notice(exit) : DialogType.multiAction(actions, exit, columns))));
    }
}
