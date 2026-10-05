package fr.noltox.hcplugins.translationkey;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.bossbar.BossBar;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import java.nio.file.*;
import java.io.IOException;
import java.time.Duration;
import java.util.*;

final class CatalogLoader {
    private CatalogLoader() {}
    static Catalog load(Path path) {
        try {
            if (Files.size(path) > 1_000_000) throw new IllegalArgumentException("Catalogue supérieur à 1 Mo.");
            return parse(Files.readString(path));
        } catch (IOException ex) { throw new IllegalArgumentException("Lecture impossible : " + path, ex); }
    }
    static Catalog parse(String text) {
        try { return compile(text); }
        catch (IllegalArgumentException ex) { throw ex; }
        catch (RuntimeException ex) { throw new IllegalArgumentException("Catalogue YAML invalide : " + ex.getMessage(), ex); }
    }
    private static Catalog compile(String text) {
        var options = new LoaderOptions();
        options.setAllowDuplicateKeys(false); options.setMaxAliasesForCollections(0);
        options.setNestingDepthLimit(Limits.DEPTH); options.setCodePointLimit(1_000_000);
        var root = map(new Yaml(new SafeConstructor(options)).load(text), "racine");
        var entries = new LinkedHashMap<String, Entry>();
        for (var node : root.entrySet())
            if (!Set.of("aliases", "settings").contains(node.getKey())) flatten(node.getKey(), node.getValue(), entries, 0);
        if (entries.isEmpty()) throw error("racine", "au moins une clé est nécessaire");
        Map<String, String> aliases = aliases(root.getOrDefault("aliases", Map.of()), entries);
        Set<String> allowed = allowed(root.getOrDefault("settings", Map.of()));
        var catalog = new Catalog(entries, aliases, allowed, Map.of());
        var visited = new HashSet<String>();
        for (Entry entry : entries.values()) visit(entry.key(), catalog, visited, new LinkedHashSet<>(), 0);
        for (Entry entry : entries.values()) {
            try {
                for (Template t : entry.templates()) t.validateReferences(catalog::entry);
                var arguments = new ArrayList<String>();
                for (int i = 0; i < entry.arity(); i++) arguments.add("1");
                String expression = entry.key() + (arguments.isEmpty() ? "" : ":" + String.join(":", arguments));
                // Representative values validate the composed MiniMessage and effects. Runtime checks still
                // validate actual arguments and PAPI values; those cannot be proven at configuration load.
                catalog.render(expression, value -> PapiBridge.validate(value, allowed), true);
                for (Template template : entry.templates()) {
                    String composed = template.render(arguments, catalog, new Limits.Budget(
                            value -> PapiBridge.validate(value, allowed)), 0);
                    Catalog.MINI.deserialize(PapiBridge.validate(composed, allowed));
                }
            } catch (RuntimeException ex) { throw error(entry.key(), ex.getMessage()); }
        }
        var constants = new HashMap<String, Catalog.Rendered>();
        for (Entry entry : entries.values())
            if (entry.arity() == 0 && !dynamic(entry, catalog, new HashSet<>()))
                constants.put(entry.key(), catalog.render(entry.key(), TextContext.IDENTITY, true));
        aliases.forEach((alias, target) -> { if (constants.containsKey(target)) constants.put(alias, constants.get(target)); });
        return new Catalog(entries, aliases, allowed, constants);
    }
    private static boolean dynamic(Entry entry, Catalog catalog, Set<String> visited) {
        if (!visited.add(entry.key())) return false;
        for (Template t : entry.templates()) if (PapiBridge.contains(t.source())) return true;
        for (String ref : entry.references()) if (dynamic(catalog.entry(ref), catalog, visited)) return true;
        return false;
    }
    private static void visit(String key, Catalog catalog, Set<String> visited, Set<String> path, int depth) {
        Limits.depth(depth);
        Entry entry = catalog.entry(key);
        key = entry.key();
        if (path.contains(key)) throw error(key, "cycle : " + String.join(" → ", path) + " → " + key);
        if (visited.contains(key)) return;
        path.add(key);
        for (String ref : entry.references()) {
            try { catalog.entry(ref); }
            catch (IllegalArgumentException ex) { throw error(key, "référence inconnue : " + ref); }
            visit(ref, catalog, visited, path, depth + 1);
        }
        path.remove(key); visited.add(key);
    }
    private static void flatten(String path, Object value, Map<String, Entry> entries, int depth) {
        Limits.depth(depth);
        if (!Expression.validKey(path)) throw error(path, "identifiant invalide");
        Entry entry;
        if (value instanceof String s) {
            InlineTags.Content inline = InlineTags.compile(s);
            if (!inline.effects().isEmpty()) {
                if (path.startsWith("theme.")) throw error(path, "les tokens du thème ne peuvent pas porter d'effets");
                var node = new LinkedHashMap<String, Object>(inline.effects());
                node.put("text", inline.text());
                flatten(path, node, entries, depth); return;
            }
            entry = Entry.create(path, Template.compile(inline.text()), null, Effects.Spec.EMPTY);
        }
        else {
            var node = map(value, path);
            boolean enriched = !path.startsWith("theme.") && (node.size() == 1 && node.containsKey("value") || node.containsKey("type")
                    || node.containsKey("text") || node.containsKey("sound")
                    || node.containsKey("actionbar") || node.containsKey("bossbar") || node.containsKey("progress")
                    || node.get("title") instanceof Map<?, ?> t && t.keySet().stream()
                        .anyMatch(Set.of("title", "subtitle", "fade-in", "stay", "fade-out")::contains));
            if (!enriched) {
                if (node.isEmpty()) throw error(path, "section vide");
                node.forEach((key, child) -> flatten(path + "." + key, child, entries, depth + 1));
                return;
            }
            only(node, path, "value", "type", "text", "sound", "actionbar", "title", "bossbar", "progress");
            if (node.containsKey("type") && !node.containsKey("value")) throw error(path, "type nécessite value");
            if (node.containsKey("value")) {
                if (node.containsKey("text")) throw error(path, "value et text sont exclusifs");
                if (!string(node, "type", "colored_text", path).equals("colored_text"))
                    throw error(path + ".type", "seul colored_text est pris en charge");
                node.put("text", node.remove("value")); node.remove("type");
            }
            if (node.containsKey("text")) {
                InlineTags.Content inline = InlineTags.compile(string(node, "text", null, path));
                node.put("text", inline.text());
                for (var effect : inline.effects().entrySet())
                    if (node.putIfAbsent(effect.getKey(), effect.getValue()) != null)
                        throw error(path + "." + effect.getKey(), "effet défini à la fois dans le texte et en propriété");
            }
            var progress = node.containsKey("progress") ? progress(map(node.get("progress"), path + ".progress"), path + ".progress") : null;
            if (progress != null && node.containsKey("text")) throw error(path, "text et progress sont exclusifs");
            Effects.Audio sound = null;
            if (node.containsKey("sound")) {
                var s = map(node.get("sound"), path + ".sound");
                only(s, path + ".sound", "key", "source", "volume", "pitch");
                String soundKey = string(s, "key", null, path + ".sound");
                if (!soundKey.contains(":") || !Key.parseable(soundKey)) throw error(path + ".sound.key", "clé namespaced attendue");
                sound = new Effects.Audio(Key.key(soundKey), choice(Sound.Source.class, s, "source", "master", path),
                        (float) numeric(s, "volume", 1, 0, 4, path), (float) numeric(s, "pitch", 1, 0.01, 2, path));
            }
            Effects.TitleSpec title = null;
            if (node.containsKey("title")) {
                var t = map(node.get("title"), path + ".title");
                only(t, path + ".title", "title", "subtitle", "fade-in", "stay", "fade-out");
                title = new Effects.TitleSpec(template(t, "title", "", path), template(t, "subtitle", "", path),
                        duration(t, "fade-in", "500ms", path), duration(t, "stay", "3s", path), duration(t, "fade-out", "500ms", path),
                        !t.containsKey("title") && t.containsKey("subtitle"));
            }
            Effects.BarSpec bar = null;
            if (node.containsKey("bossbar")) {
                var b = map(node.get("bossbar"), path + ".bossbar");
                only(b, path + ".bossbar", "text", "color", "overlay", "progress", "duration");
                long millis = duration(b, "duration", "3s", path).toMillis();
                if (millis == 0) throw error(path + ".bossbar.duration", "durée positive attendue");
                bar = new Effects.BarSpec(template(b, "text", null, path), choice(BossBar.Color.class, b, "color", "yellow", path),
                        choice(BossBar.Overlay.class, b, "overlay", "progress", path),
                        (float) numeric(b, "progress", 1, 0, 1, path), (millis + 49) / 50);
            }
            entry = Entry.create(path, template(node, "text", "", path), progress,
                    new Effects.Spec(sound, node.containsKey("actionbar") ? template(node, "actionbar", null, path) : null, title, bar));
        }
        if (entries.putIfAbsent(path, entry) != null) throw error(path, "clé dupliquée");
        if (entries.size() > 4096) throw error(path, "plus de 4096 clés");
    }
    private static Entry.Progress progress(Map<String, Object> p, String path) {
        only(p, path, "length", "completed", "current", "remaining", "value", "maximum");
        double length = numeric(p, "length", 10, 1, 100, path);
        if (length != Math.floor(length)) throw error(path + ".length", "entier attendu");
        return new Entry.Progress((int) length, template(p, "completed", "<green>|</green>", path),
                template(p, "current", "<yellow>|</yellow>", path), template(p, "remaining", "<gray>|</gray>", path),
                template(p, "value", "{0}", path), template(p, "maximum", "{1}", path));
    }
    private static Map<String, String> aliases(Object value, Map<String, Entry> entries) {
        var raw = map(value, "aliases");
        if (raw.size() > 4096) throw error("aliases", "plus de 4096 alias");
        var result = new HashMap<String, String>();
        for (String alias : raw.keySet()) {
            if (!Expression.validKey(alias) || entries.containsKey(alias)) throw error("aliases." + alias, "identifiant invalide ou collision");
            var seen = new HashSet<String>();
            String target = alias;
            while (raw.containsKey(target)) {
                if (!seen.add(target)) throw error("aliases." + alias, "cycle");
                if (seen.size() > Limits.DEPTH) throw error("aliases." + alias, "chaîne trop longue");
                target = string(raw, target, null, "aliases");
            }
            if (!entries.containsKey(target)) throw error("aliases." + alias, "cible inconnue : " + target);
            result.put(alias, target);
        }
        return result;
    }
    private static Set<String> allowed(Object value) {
        var settings = map(value, "settings"); only(settings, "settings", "allowed-placeholders");
        Object raw = settings.getOrDefault("allowed-placeholders", List.of("player_name"));
        if (!(raw instanceof List<?> list) || list.size() > 256) throw error("settings.allowed-placeholders", "liste de 256 noms maximum attendue");
        var allowed = new HashSet<String>();
        for (Object item : list) {
            if (!(item instanceof String name) || !name.matches("[a-zA-Z0-9_:.\\-]+"))
                throw error("settings.allowed-placeholders", "identifiant PAPI sans % attendu");
            if (name.startsWith("hcextra_tkey_") || name.startsWith("hcextra_checkitem_give") ||
                    name.startsWith("hcextra_checkitem_remove") || name.startsWith("checkitem_give") || name.startsWith("checkitem_remove"))
                throw error("settings.allowed-placeholders", "récursion ou placeholder à effets interdit : " + name);
            allowed.add(name);
        }
        return Set.copyOf(allowed);
    }
    private static Map<String, Object> map(Object value, String path) {
        if (!(value instanceof Map<?, ?> source)) throw error(path, "mapping ou texte attendu");
        var result = new LinkedHashMap<String, Object>();
        source.forEach((k, v) -> {
            if (!(k instanceof String key)) throw error(path, "clé texte attendue");
            result.put(key, v);
        });
        return result;
    }
    private static void only(Map<String, Object> node, String path, String... allowed) {
        var names = Set.of(allowed);
        for (String key : node.keySet()) if (!names.contains(key)) throw error(path + "." + key, "propriété inconnue");
    }
    private static String string(Map<String, Object> node, String key, String fallback, String path) {
        Object value = node.getOrDefault(key, fallback);
        if (!(value instanceof String s)) throw error(path + "." + key, "texte requis");
        return Limits.text(s);
    }
    private static Template template(Map<String, Object> node, String key, String fallback, String path) {
        try { return Template.compile(string(node, key, fallback, path)); }
        catch (IllegalArgumentException ex) { throw error(path + "." + key, ex.getMessage()); }
    }
    private static double numeric(Map<String, Object> node, String key, double fallback, double min, double max, String path) {
        Object raw = node.getOrDefault(key, fallback);
        if (!(raw instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue() < min || n.doubleValue() > max)
            throw error(path + "." + key, "nombre entre " + min + " et " + max + " attendu");
        return n.doubleValue();
    }
    private static <T extends Enum<T>> T choice(Class<T> type, Map<String, Object> node, String key, String fallback, String path) {
        try { return Enum.valueOf(type, string(node, key, fallback, path).toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { throw error(path + "." + key, "valeur autorisée : " + Arrays.toString(type.getEnumConstants())); }
    }
    private static Duration duration(Map<String, Object> node, String key, String fallback, String path) {
        String raw = string(node, key, fallback, path);
        long factor = raw.endsWith("ms") ? 1 : raw.endsWith("t") ? 50 : raw.endsWith("s") ? 1000 : 0;
        try {
            if (factor == 0) throw new NumberFormatException();
            long n = Long.parseLong(raw.substring(0, raw.length() - (factor == 1 ? 2 : 1)));
            long ms = Math.multiplyExact(n, factor);
            if (ms < 0 || ms > 300_000) throw new NumberFormatException();
            return Duration.ofMillis(ms);
        } catch (ArithmeticException | NumberFormatException ex) { throw error(path + "." + key, "durée entière entre 0 et 300s, suffixe ms/s/t attendu"); }
    }
    private static IllegalArgumentException error(String path, String detail) { return new IllegalArgumentException(path + " : " + detail); }
}
