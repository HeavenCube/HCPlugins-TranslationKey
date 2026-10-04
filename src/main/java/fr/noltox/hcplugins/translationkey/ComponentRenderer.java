package fr.noltox.hcplugins.translationkey;

import net.kyori.adventure.text.*;
import java.util.*;

/** Only literal text nodes: existing styles/events, translatable keys and signed payloads stay untouched. */
final class ComponentRenderer {
    record Result(Component component, List<Effects.Effect> effects, boolean changed) {}
    private final Catalog catalog;
    ComponentRenderer(Catalog catalog) { this.catalog = catalog; }
    Result replace(Component input, TextContext context, boolean dispatch) {
        var effects = new LinkedHashSet<Effects.Effect>();
        var budget = new Limits.Budget();
        Component result = visit(input, context, dispatch, effects, budget, 0);
        return new Result(result, List.copyOf(effects), result != input);
    }
    private Component visit(Component input, TextContext context, boolean dispatch, Set<Effects.Effect> effects,
                            Limits.Budget budget, int depth) {
        budget.step(depth);
        Component output = input;
        if (input instanceof TextComponent text && text.content().contains(Markers.OPEN)) {
            String source = text.content(); Limits.text(source);
            var children = new ArrayList<Component>();
            int at = 0, start;
            while ((start = source.indexOf(Markers.OPEN, at)) >= 0) {
                budget.step(depth);
                if (start > at) children.add(Component.text(source.substring(at, start)));
                int end = Markers.end(source, start);
                var rendered = catalog.render(source.substring(start + Markers.OPEN.length(), end - 2), context, dispatch);
                budget.characters(rendered.miniMessage().length());
                children.add(rendered.component()); effects.addAll(rendered.effects());
                at = end;
            }
            if (at < source.length()) children.add(Component.text(source.substring(at)));
            output = text.content("").children(children);
        }
        if (!input.children().isEmpty()) {
            var children = new ArrayList<Component>(input.children().size());
            boolean changed = false;
            for (Component child : input.children()) {
                Component replacement = visit(child, context, dispatch, effects, budget, depth + 1);
                children.add(replacement); changed |= replacement != child;
            }
            if (output != input) output = output.children(concat(output.children(), children));
            else if (changed) output = input.children(children);
        }
        return output;
    }
    private static List<Component> concat(List<Component> a, List<Component> b) {
        var result = new ArrayList<Component>(a.size() + b.size()); result.addAll(a); result.addAll(b); return result;
    }
    static boolean contains(Component component) {
        return contains(component, 0, new Limits.Budget());
    }
    private static boolean contains(Component c, int depth, Limits.Budget budget) {
        budget.step(depth);
        if (c instanceof TextComponent text && text.content().contains(Markers.OPEN)) return true;
        for (Component child : c.children()) if (contains(child, depth + 1, budget)) return true;
        return false;
    }
}
