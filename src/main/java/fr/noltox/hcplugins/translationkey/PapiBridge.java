package fr.noltox.hcplugins.translationkey;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import java.util.Set;
import java.util.regex.Pattern;

/** Explicit read-only allowlist: never delegate arbitrary effectful expansions or recursive tkey calls. */
final class PapiBridge {
    private static final Pattern PLACEHOLDER = Pattern.compile("%([a-zA-Z0-9_:.\\-]+)%");
    private PapiBridge() {}
    static boolean contains(String text) { return text.indexOf('%') >= 0 && PLACEHOLDER.matcher(text).find(); }
    static String validate(String text, Set<String> allowed) {
        var matcher = PLACEHOLDER.matcher(text);
        return matcher.replaceAll(match -> {
            requireAllowed(match.group(1), allowed);
            return "1";
        });
    }
    static TextContext context(OfflinePlayer player, Set<String> allowed) {
        var values = new java.util.HashMap<String, String>();
        return text -> {
            if (!contains(text)) return text;
            if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("La résolution PAPI dynamique exige le thread serveur.");
            var matcher = PLACEHOLDER.matcher(text);
            return Limits.text(matcher.replaceAll(match -> {
                requireAllowed(match.group(1), allowed);
                String result = values.computeIfAbsent(match.group(), key -> PlaceholderAPI.setPlaceholders(player, key));
                // PAPI data is literal text: it cannot introduce tags, markers, or another expansion pass.
                return java.util.regex.Matcher.quoteReplacement(Catalog.MINI.escapeTags(result).replace("[[hctkey:", "[[hctkey\\:"));
            }));
        };
    }
    private static void requireAllowed(String name, Set<String> allowed) {
        if (!allowed.contains(name)) throw new IllegalArgumentException("Placeholder PAPI non autorisé : " + name
                + " (settings.allowed-placeholders ; autoriser uniquement des valeurs sans effets).");
    }
}
