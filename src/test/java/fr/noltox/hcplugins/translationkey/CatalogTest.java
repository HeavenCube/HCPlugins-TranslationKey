package fr.noltox.hcplugins.translationkey;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class CatalogTest {
    @Test void runtimeArgumentsDoNotInterpretPositionalParametersAgain() {
        var c = catalog("a: '<gold>{0}</gold>'");
        assertEquals("<gold>{0}</gold>", c.render("a:{0}", TextContext.IDENTITY, false).miniMessage());
        assertEquals("<gold><gold>{0}</gold></gold>", c.render("a:[[hctkey:a:{0}]]", TextContext.IDENTITY, false).miniMessage());
    }
    private Catalog catalog(String yaml) { return CatalogLoader.parse(yaml); }
    @Test void nestedTemplatesAndArguments() {
        var c = catalog("""
                color: '#FBC62C'
                fragment: '<[[hctkey:color]]>{0}</[[hctkey:color]]>'
                parent: '[[hctkey:fragment:{0}]] / [[hctkey:fragment:{1}]]'
                leaf: 'Pêcheur'
                """);
        assertEquals("<#FBC62C>Métiers</#FBC62C> / <#FBC62C>Pêcheur</#FBC62C>",
                c.render("parent:Métiers:[[hctkey:leaf]]", TextContext.IDENTITY, false).miniMessage());
        assertThrows(IllegalArgumentException.class, () -> c.render("parent:one", TextContext.IDENTITY, false));
    }
    @Test void papiRunsAfterCompositionAndOnlyOnce() {
        var c = catalog("greeting: '<gray>{0} %player_name%</gray>'");
        AtomicInteger calls = new AtomicInteger();
        var result = c.render("greeting:Salut", text -> { calls.incrementAndGet(); return text.replace("%player_name%", "Noltox"); }, false);
        assertEquals("<gray>Salut Noltox</gray>", result.miniMessage());
        assertEquals(1, calls.get());
    }
    @Test void cyclesMissingReferencesAndAliasesRejected() {
        for (String yaml : List.of("a: '[[hctkey:a]]'", "a: '[[hctkey:b]]'\nb: '[[hctkey:a]]'",
                "a: '[[hctkey:unknown]]'", "aliases: {a: b, b: a}", "a: x\naliases: {a: a}",
                "a: x\naliases: {b: missing}"))
            assertThrows(IllegalArgumentException.class, () -> catalog(yaml), yaml);
        var c = catalog("ui: {close: Fermer}\naliases: {old.close-message: ui.close}");
        assertEquals("Fermer", c.render("old.close-message", TextContext.IDENTITY, false).miniMessage());
    }
    @Test void onlyTopLevelEffectsAndDeduplication() {
        var c = catalog("""
                child:
                  text: enfant
                  sound: {key: 'minecraft:block.note_block.bass'}
                parent: '[[hctkey:child]]'
                """);
        assertTrue(c.render("child", TextContext.IDENTITY, false).effects().isEmpty());
        assertTrue(c.render("parent", TextContext.IDENTITY, true).effects().isEmpty());
        var rendered = new ComponentRenderer(c).replace(net.kyori.adventure.text.Component.text(
                "[[hctkey:child]] et [[hctkey:child]]"), TextContext.IDENTITY, true);
        assertEquals(1, rendered.effects().size());
    }
    @Test void progressClampsAndRejectsNonFiniteValues() {
        var c = catalog("""
                bar:
                  progress:
                    length: 4
                    completed: '<green>|</green>'
                    current: '<yellow>!</yellow>'
                    remaining: '<gray>.</gray>'
                    value: '{0}'
                    maximum: '{1}'
                """);
        assertEquals("<green>|</green>".repeat(2) + "<yellow>!</yellow><gray>.</gray>",
                c.render("bar:5:10", TextContext.IDENTITY, false).miniMessage());
        assertEquals("<green>|</green>".repeat(4), c.render("bar:20:10", TextContext.IDENTITY, false).miniMessage());
        for (String expr : List.of("bar:NaN:10", "bar:3:0", "bar:Infinity:4"))
            assertThrows(IllegalArgumentException.class, () -> c.render(expr, TextContext.IDENTITY, false));
    }
    @Test void invalidConfigurationsRejectedWithPath() {
        for (String yaml : List.of("a: 12", "a: x\na: y", "a: '<red>oops</blue>'", "a: {sound: {key: 'Bad:Key'}}",
                "a: {sound: {key: 'minecraft:a', volume: .nan}}",
                "a: {title: {stay: '-1s'}}", "a: {bossbar: {text: a, progress: 2}}",
                "a: {progress: {length: 0}}", "a: {text: a, typo: b}", "a: '{17}'",
                "a: '{1}'", "a: '[[hctkey:b:x]]'\nb: plain"))
            assertThrows(IllegalArgumentException.class, () -> catalog(yaml), yaml);
    }
    @Test void reloadPublishesOnlyCompleteGenerations() throws Exception {
        var store = new CatalogStore(catalog("a: before"));
        var old = store.current();
        assertThrows(IllegalArgumentException.class, () -> store.reload(() -> catalog("a: '[[hctkey:missing]]'")));
        assertSame(old, store.current());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var reader = executor.submit(() -> {
                for (int i = 0; i < 1000; i++)
                    assertTrue(List.of("before", "after").contains(store.current().render("a", TextContext.IDENTITY, false).miniMessage()));
            });
            store.reload(() -> catalog("a: after"));
            reader.get(5, TimeUnit.SECONDS);
        }
        assertEquals("before", old.render("a", TextContext.IDENTITY, false).miniMessage());
        assertEquals("after", store.current().render("a", TextContext.IDENTITY, false).miniMessage());
    }
    @Test void defaultConfigurationCompiles() throws Exception {
        try (var input = getClass().getResourceAsStream("/config.yml")) {
            assertNotNull(input);
            var defaults = catalog(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            assertTrue(defaults.keys().size() > 10);
            assertTrue(defaults.aliases().isEmpty(), "Aucun alias déprécié ne doit être actif par défaut");
        }
    }
}
