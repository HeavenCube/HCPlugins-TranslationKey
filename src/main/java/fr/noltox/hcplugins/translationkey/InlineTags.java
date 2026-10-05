package fr.noltox.hcplugins.translationkey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Configuration-only syntax sugar. Effects become ordinary specs before publication, never runtime tags. */
final class InlineTags {
    private static final Set<String> EFFECTS = Set.of("sound", "actionbar", "title", "subtitle", "bossbar");
    record Content(String text, Map<String, Object> effects) {}
    private InlineTags() {}

    static Content compile(String source) {
        var effects = new LinkedHashMap<String, Object>();
        String text = rewrite(source, effects, 0);
        return new Content(text, Map.copyOf(effects));
    }

    static String text(String source) { return rewrite(source, null, 0); }

    private static String rewrite(String source, Map<String, Object> effects, int depth) {
        Limits.depth(depth); Limits.text(source);
        var out = new StringBuilder();
        int tags = 0;
        for (int i = 0; i < source.length();) {
            if (source.charAt(i) == '\\') {
                out.append(source.charAt(i++));
                if (i < source.length()) out.append(source.charAt(i++));
                continue;
            }
            if (source.startsWith(Markers.OPEN, i)) {
                int end = Markers.end(source, i);
                out.append(source, i, end); i = end; continue;
            }
            if (source.charAt(i) != '<') { out.append(source.charAt(i++)); continue; }
            int nameEnd = i + 1;
            while (nameEnd < source.length() && source.charAt(nameEnd) != ':' && source.charAt(nameEnd) != '>') nameEnd++;
            String name = source.substring(i + 1, nameEnd).toLowerCase(Locale.ROOT);
            boolean custom = name.equals("p") || name.equals("papi") || EFFECTS.contains(name);
            int end = end(source, i);
            if (end < 0) {
                if (custom) throw invalid(name, "balise non fermée");
                out.append(source.charAt(i++)); continue;
            }
            if (!custom) { out.append(source, i, end); i = end; continue; }
            if (++tags > Limits.OPERATIONS) throw invalid(name, "trop de balises");
            var args = Expression.parse(name + source.substring(nameEnd, end - 1)).arguments();
            switch (name) {
                case "p" -> {
                    if (args.isEmpty() || !Expression.validKey(args.getFirst())) throw invalid(name, "clé du catalogue attendue");
                    var values = new ArrayList<String>();
                    for (int a = 1; a < args.size(); a++) values.add(rewrite(args.get(a), null, depth + 1));
                    out.append(Markers.OPEN).append(expression(args.getFirst(), values)).append("]]");
                }
                case "papi" -> {
                    count(name, args, 1);
                    String key = args.getFirst();
                    if (!key.matches("[a-zA-Z0-9_:.\\-]+")) throw invalid(name, "nom PAPI sans % attendu");
                    out.append('%').append(key).append('%');
                }
                default -> {
                    if (effects == null) throw invalid(name, "effet autorisé uniquement dans le texte principal d'une entrée");
                    effect(name, args, effects, depth + 1);
                }
            }
            if (out.length() > Limits.OUTPUT) throw invalid(name, "contenu trop long");
            i = end;
        }
        return Limits.text(out.toString());
    }

    private static int end(String source, int start) {
        char quote = 0;
        for (int i = start + 1; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '\\') { i++; continue; }
            if (quote != 0) { if (c == quote) quote = 0; }
            else if ((c == '\'' || c == '"') && source.charAt(i - 1) == ':') quote = c;
            else if (c == '>') return i + 1;
            else if (c == '<') return -1;
        }
        return -1;
    }

    private static String expression(String key, List<String> arguments) {
        var out = new StringBuilder(key);
        for (String value : arguments)
            out.append(":\"").append(value.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        return out.toString();
    }

    private static void effect(String name, List<String> original, Map<String, Object> effects, int depth) {
        var args = new ArrayList<>(original);
        switch (name) {
            case "sound" -> {
                // A namespaced key may be quoted, or written as <sound:minecraft:block.chain.break:1:0.5>.
                if (args.size() >= 2 && args.get(0).matches("[a-z0-9_.-]+") && args.get(1).matches("[a-z_][a-z0-9_./-]*")) {
                    String namespace = args.removeFirst();
                    args.set(0, namespace + ":" + args.getFirst());
                }
                if (args.size() != 1 && args.size() != 3) throw invalid(name, "son ou son:volume:pitch attendu");
                String key = args.getFirst();
                if (key.matches("[A-Z][A-Z0-9_]*"))
                    throw invalid(name, "utiliser une clé Minecraft, par exemple entity.experience_orb.pickup, plutôt qu'un nom Bukkit");
                if (!key.contains(":")) key = "minecraft:" + key;
                put(effects, "sound", Map.of("key", key, "volume", args.size() == 3 ? number(name, args.get(1)) : 1d,
                        "pitch", args.size() == 3 ? number(name, args.get(2)) : 1d));
            }
            case "actionbar" -> {
                count(name, args, 1); put(effects, name, rewrite(args.getFirst(), null, depth));
            }
            case "title", "subtitle" -> {
                boolean title = name.equals("title");
                if (args.size() != 1 && args.size() != 4 && !(title && (args.size() == 2 || args.size() == 5)))
                    throw invalid(name, "texte(s), avec éventuellement fadeIn:stay:fadeOut en ticks");
                int at = args.size() <= 2 ? 0 : 3;
                var spec = new LinkedHashMap<String, Object>();
                if (at != 0) {
                    spec.put("fade-in", ticks(name, args.get(0))); spec.put("stay", ticks(name, args.get(1)));
                    spec.put("fade-out", ticks(name, args.get(2)));
                }
                spec.put(name, rewrite(args.get(at), null, depth));
                if (title) spec.put("subtitle", args.size() > at + 1 ? rewrite(args.get(at + 1), null, depth) : "");
                put(effects, "title", spec);
            }
            case "bossbar" -> {
                if (args.size() != 4 && args.size() != 5) throw invalid(name, "texte:progression:couleur:style[:durée en ticks] attendu");
                String overlay = args.get(3).equalsIgnoreCase("solid") ? "progress" : args.get(3);
                put(effects, name, Map.of("text", rewrite(args.getFirst(), null, depth), "progress", number(name, args.get(1)) / 100,
                        "color", args.get(2), "overlay", overlay, "duration", args.size() == 5 ? ticks(name, args.get(4)) : "60t"));
            }
            default -> throw new IllegalArgumentException("Balise d'effet inconnue : " + name);
        }
    }

    private static String ticks(String name, String value) {
        try {
            long ticks = Long.parseLong(value);
            if (ticks < 0 || ticks > 6000) throw new NumberFormatException();
            return ticks + "t";
        } catch (NumberFormatException ex) { throw invalid(name, "durée entière entre 0 et 6000 ticks attendue"); }
    }
    private static double number(String name, String value) {
        try {
            double number = Double.parseDouble(value);
            if (Double.isFinite(number)) return number;
        } catch (NumberFormatException ignored) { /* Meaningful configuration error below. */ }
        throw invalid(name, "nombre fini attendu : " + value);
    }
    private static void put(Map<String, Object> effects, String key, Object value) {
        if (effects.putIfAbsent(key, value) != null) throw invalid(key, "un seul effet de ce type par entrée");
    }
    private static void count(String name, List<String> args, int expected) {
        if (args.size() != expected) throw invalid(name, expected + " argument(s) attendu(s)");
    }
    private static IllegalArgumentException invalid(String name, String detail) {
        return new IllegalArgumentException("<" + name + "> : " + detail);
    }
}
