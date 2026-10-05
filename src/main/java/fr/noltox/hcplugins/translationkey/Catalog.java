package fr.noltox.hcplugins.translationkey;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import java.util.*;

final class Catalog {
    static final MiniMessage MINI = MiniMessage.builder().strict(true).build();
    record Rendered(String miniMessage, Component component, List<Effects.Effect> effects) {}
    private final Map<String, Entry> entries;
    private final Map<String, String> aliases;
    private final Map<String, Rendered> constants;
    private final Set<String> allowedPlaceholders;
    private final List<String> keys;

    Catalog(Map<String, Entry> entries, Map<String, String> aliases, Set<String> allowedPlaceholders,
            Map<String, Rendered> constants) {
        this.entries = Map.copyOf(entries); this.aliases = Map.copyOf(aliases);
        this.allowedPlaceholders = Set.copyOf(allowedPlaceholders); this.constants = Map.copyOf(constants);
        this.keys = java.util.stream.Stream.concat(entries.keySet().stream(), aliases.keySet().stream()).sorted().toList();
    }
    List<String> keys() { return keys; }
    Map<String, String> aliases() { return aliases; }
    Set<String> allowedPlaceholders() { return allowedPlaceholders; }
    Entry entry(String key) {
        Entry entry = entries.get(aliases.getOrDefault(key, key));
        if (entry == null) throw new IllegalArgumentException("Clé inconnue : " + key);
        return entry;
    }
    Rendered render(String expression, TextContext context, boolean dispatch) {
        Rendered constant = constants.get(expression);
        if (constant != null) return dispatch ? constant : new Rendered(constant.miniMessage(), constant.component(), List.of());
        Expression parsed = Expression.parse(expression);
        var budget = new Limits.Budget(context);
        var arguments = new ArrayList<String>(parsed.arguments().size());
        for (String argument : parsed.arguments())
            arguments.add(Template.argument(argument).render(List.of(), this, budget, 0));
        Entry entry = entry(parsed.key());
        String mini = Limits.text(context.expand(text(parsed.key(), arguments, budget, 0)));
        Component component = MINI.deserialize(mini);
        var effects = new ArrayList<Effects.Effect>(4);
        if (dispatch) {
            Effects.Spec spec = entry.effects();
            if (spec.sound() != null) effects.add(spec.sound());
            if (spec.actionbar() != null) effects.add(new Effects.Actionbar(component(spec.actionbar(), arguments, budget)));
            if (spec.title() != null) {
                var t = spec.title();
                if (t.subtitleOnly()) effects.add(new Effects.Subtitle(component(t.subtitle(), arguments, budget), t.fadeIn(), t.stay(), t.fadeOut()));
                else effects.add(new Effects.Title(component(t.title(), arguments, budget), component(t.subtitle(), arguments, budget),
                        t.fadeIn(), t.stay(), t.fadeOut()));
            }
            if (spec.bossbar() != null) {
                var b = spec.bossbar();
                effects.add(new Effects.Bar(component(b.text(), arguments, budget), b.color(), b.overlay(), b.progress(), b.ticks()));
            }
        }
        return new Rendered(mini, component, List.copyOf(effects));
    }
    private Component component(Template template, List<String> args, Limits.Budget budget) {
        return MINI.deserialize(Limits.text(budget.context.expand(template.render(args, this, budget, 0))));
    }
    String text(String key, List<String> args, Limits.Budget budget, int depth) {
        budget.step(depth);
        Entry entry = entry(key); entry.checkArguments(args.size());
        Rendered constant = constants.get(entry.key());
        if (constant != null) return constant.miniMessage();
        if (entry.progress() == null) return entry.text().render(args, this, budget, depth + 1);
        var p = entry.progress();
        double value = number(budget.context.expand(p.value().render(args, this, budget, depth + 1)), key + ".progress.value");
        double max = number(budget.context.expand(p.maximum().render(args, this, budget, depth + 1)), key + ".progress.maximum");
        if (max <= 0) throw new IllegalArgumentException(key + ".progress.maximum doit être > 0.");
        int complete = (int) Math.floor(Math.clamp(value / max, 0, 1) * p.length());
        var out = new StringBuilder();
        for (int i = 0; i < p.length(); i++) {
            Template cell = i < complete ? p.completed() : i == complete ? p.current() : p.remaining();
            String part = cell.render(args, this, budget, depth + 1);
            if (out.length() + part.length() > Limits.OUTPUT) throw new IllegalArgumentException(key + " : barre trop longue.");
            out.append(part);
        }
        return out.toString();
    }
    static double number(String text, String path) {
        try {
            double value = Double.parseDouble(text);
            if (Double.isFinite(value)) return value;
        } catch (NumberFormatException ignored) { /* The contextual error below is useful to the administrator. */ }
        throw new IllegalArgumentException(path + " : nombre fini attendu, reçu " + text);
    }
}
