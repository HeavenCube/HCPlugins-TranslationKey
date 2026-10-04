package fr.noltox.hcplugins.translationkey;

import fr.noltox.hcplugins.core.api.HCPluginsCore;
import fr.noltox.hcplugins.core.api.command.CoreCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.*;

final class TranslationCommand implements CoreCommand {
    private static final List<String> ACTIONS = List.of("reload", "validate", "get", "send", "test", "search", "list");
    private final HCTranslationKey plugin;
    TranslationCommand(HCTranslationKey plugin) { this.plugin = plugin; }
    @Override public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (!sender.hasPermission(HCTranslationKey.PERMISSION)) { sender.sendMessage(HCPluginsCore.translations(plugin).noPermission()); return; }
        if (args.length == 0) {
            if (sender instanceof Player player) new AdminDialog(plugin).open(player);
            else help(sender);
            return;
        }
        try {
            String expression = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "reload" -> plugin.load(sender, true);
                case "validate" -> plugin.load(sender, false);
                case "get" -> {
                    Catalog c = plugin.catalog();
                    var result = c.render(expression, PapiBridge.context(sender instanceof Player p ? p : null, c.allowedPlaceholders()), false);
                    sender.sendMessage(Component.text("MiniMessage : " + result.miniMessage()));
                    sender.sendMessage(result.component());
                }
                case "send", "test" -> {
                    Player target = sender instanceof Player player ? player : null;
                    // Explicit --player avoids ambiguity when an argument itself is a player's name.
                    int end = args.length;
                    if (args.length >= 4 && args[args.length - 2].equals("--player")) {
                        target = plugin.getServer().getPlayerExact(args[args.length - 1]); end -= 2;
                    } else if (args.length >= 3) {
                        Player named = plugin.getServer().getPlayerExact(args[args.length - 1]);
                        if (named != null) { target = named; end--; }
                    }
                    if (target == null) throw new IllegalArgumentException("Joueur connecté requis : send <expression> --player <joueur>.");
                    dispatch(target, String.join(" ", Arrays.copyOfRange(args, 1, end)));
                    if (sender != target) sender.sendMessage(Component.text("Contenu envoyé à " + target.getName() + ".", NamedTextColor.GREEN));
                }
                case "search", "list" -> {
                    boolean prefix = args[0].equalsIgnoreCase("list");
                    String filter = expression.toLowerCase(Locale.ROOT);
                    List<String> matches = plugin.catalog().keys().stream()
                            .filter(key -> prefix ? key.startsWith(filter) : key.contains(filter)).toList();
                    sender.sendMessage(Component.text(matches.size() + " résultat(s) — 50 premiers :"));
                    matches.stream().limit(50).forEach(key -> sender.sendMessage(Component.text(key)));
                }
                default -> help(sender);
            }
        } catch (IllegalArgumentException | IllegalStateException ex) {
            sender.sendMessage(Component.text(ex.getMessage(), NamedTextColor.RED));
        }
    }
    void dispatch(Player target, String expression) {
        Catalog c = plugin.catalog();
        var result = c.render(expression, PapiBridge.context(target, c.allowedPlaceholders()), true);
        target.sendMessage(result.component());
        plugin.effects().dispatch(target, result.effects());
    }
    private static void help(CommandSender sender) {
        sender.sendMessage(Component.text("/hcplugins tkey " + String.join(" | ", ACTIONS)));
        sender.sendMessage(Component.text("send/test <expression> [--player joueur] ; get <expression> ; list [préfixe] ; search <texte>"));
    }
    @Override public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (!source.getSender().hasPermission(HCTranslationKey.PERMISSION)) return List.of();
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return ACTIONS.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        if (Set.of("get", "send", "test", "list").contains(args[0]) && args.length == 2)
            return plugin.catalog().keys().stream().filter(s -> s.startsWith(args[1])).limit(50).toList();
        if (args.length > 2 && args[args.length - 2].equals("--player"))
            return plugin.getServer().getOnlinePlayers().stream().map(Player::getName)
                    .filter(s -> s.startsWith(args[args.length - 1])).toList();
        return List.of();
    }
}
