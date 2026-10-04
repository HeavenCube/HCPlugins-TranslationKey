package fr.noltox.hcplugins.translationkey;

import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;

record Entry(String key, Template text, Progress progress, Effects.Spec effects, int arity, Set<String> references) {
    record Progress(int length, Template completed, Template current, Template remaining, Template value, Template maximum) {
        List<Template> templates() { return List.of(completed, current, remaining, value, maximum); }
    }
    static Entry create(String key, Template text, Progress progress, Effects.Spec effects) {
        var templates = new ArrayList<Template>();
        templates.add(text);
        if (progress != null) templates.addAll(progress.templates());
        templates.addAll(effects.templates());
        var refs = new HashSet<String>();
        var params = new HashSet<Integer>();
        for (Template t : templates) { refs.addAll(t.references()); params.addAll(t.parameters()); }
        int arity = params.stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
        if (params.size() != arity) throw new IllegalArgumentException(key + " : indices {0}..{n} obligatoirement contigus.");
        return new Entry(key, text, progress, effects, arity, Set.copyOf(refs));
    }
    List<Template> templates() {
        var templates = new ArrayList<Template>();
        templates.add(text);
        if (progress != null) templates.addAll(progress.templates());
        templates.addAll(effects.templates());
        return templates;
    }
    void checkArguments(int count) {
        if (count != arity) throw new IllegalArgumentException(key + " : " + arity + " argument(s) attendu(s), reçu " + count + ".");
    }
}
