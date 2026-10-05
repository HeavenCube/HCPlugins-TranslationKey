package fr.noltox.hcplugins.translationkey;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.function.Function;

record Template(String source, List<Part> parts, Set<Integer> parameters, Set<String> references) {
    sealed interface Part permits Literal, Argument, Reference {}
    record Literal(String value) implements Part {}
    record Argument(int index) implements Part {}
    record Reference(String key, List<Template> arguments) implements Part {}

    static Template compile(String source) { return compile(InlineTags.text(source), 0, true); }
    static Template argument(String source) { return compile(InlineTags.text(source), 0, false); }
    private static Template compile(String source, int depth, boolean parametersEnabled) {
        Limits.depth(depth); Limits.text(source);
        var parts = new ArrayList<Part>();
        var parameters = new HashSet<Integer>();
        var references = new HashSet<String>();
        var literal = new StringBuilder();
        for (int i = 0; i < source.length();) {
            if (source.startsWith(Markers.OPEN, i)) {
                flush(parts, literal);
                int end = Markers.end(source, i);
                Expression expr = Expression.parse(source.substring(i + Markers.OPEN.length(), end - 2));
                var arguments = new ArrayList<Template>();
                for (String argument : expr.arguments()) {
                    Template t = compile(InlineTags.text(argument), depth + 1, parametersEnabled);
                    arguments.add(t); parameters.addAll(t.parameters()); references.addAll(t.references());
                }
                parts.add(new Reference(expr.key(), List.copyOf(arguments)));
                references.add(expr.key()); i = end;
            } else if (parametersEnabled && source.charAt(i) == '{' && i + 1 < source.length() && Character.isDigit(source.charAt(i + 1))) {
                int end = source.indexOf('}', i + 1);
                if (end < 0) throw new IllegalArgumentException("Paramètre non fermé : " + source);
                int index;
                try { index = Integer.parseInt(source.substring(i + 1, end)); }
                catch (NumberFormatException ex) { throw new IllegalArgumentException("Paramètre invalide : " + source, ex); }
                if (index >= Limits.ARGUMENTS) throw new IllegalArgumentException("Indice de paramètre trop grand : " + index);
                flush(parts, literal); parts.add(new Argument(index)); parameters.add(index); i = end + 1;
            } else literal.append(source.charAt(i++));
        }
        flush(parts, literal);
        return new Template(source, List.copyOf(parts), Set.copyOf(parameters), Set.copyOf(references));
    }
    private static void flush(List<Part> parts, StringBuilder literal) {
        if (!literal.isEmpty()) { parts.add(new Literal(literal.toString())); literal.setLength(0); }
    }
    String render(List<String> arguments, Catalog catalog, Limits.Budget budget, int depth) {
        budget.step(depth);
        var out = new StringBuilder();
        for (Part part : parts) {
            String value = switch (part) {
                case Literal l -> l.value();
                case Argument a -> arguments.get(a.index());
                case Reference r -> {
                    var args = new ArrayList<String>(r.arguments().size());
                    for (Template t : r.arguments()) args.add(t.render(arguments, catalog, budget, depth + 1));
                    yield catalog.text(r.key(), args, budget, depth + 1);
                }
            };
            if (out.length() + value.length() > Limits.OUTPUT) throw new IllegalArgumentException("Contenu résolu trop long.");
            out.append(value);
        }
        return out.toString();
    }
    void validateReferences(Function<String, Entry> entries) {
        for (Part part : parts) if (part instanceof Reference r) {
            Entry target = entries.apply(r.key());
            target.checkArguments(r.arguments().size());
            r.arguments().forEach(t -> t.validateReferences(entries));
        }
    }
}
