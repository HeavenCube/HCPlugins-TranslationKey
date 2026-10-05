package fr.noltox.hcplugins.translationkey;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class InlineTagsTest {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static String plain(Catalog.Rendered result) { return PLAIN.serialize(result.component()); }

    @Test void allThreeRequestedExamplesCompileAndDispatchSound() {
        var c = CatalogLoader.parse("""
                prefix: '<gold>H</gold>'
                erreur-impossible:
                  value: '<sound:block.chain.break:1.00:0.50><p:prefix> <c:#E62E39>Action impossible, {0}…</c>'
                  type: colored_text
                erreur-divers:
                  value: '<sound:block.chain.break:1.00:0.50><p:prefix> <c:#E62E39>{0}</c>'
                  type: colored_text
                msg-reward:
                  value: '<sound:block.trial_spawner.eject_item:1.00:1.00><p:prefix> <c:#CCCCCC>Tu viens de recevoir la récompense {0} !</c>'
                  type: colored_text
                """);
        var impossible = c.render("erreur-impossible:ici", TextContext.IDENTITY, true);
        assertEquals("H Action impossible, ici…", plain(impossible));
        assertEquals("H problème", plain(c.render("erreur-divers:problème", TextContext.IDENTITY, true)));
        var reward = c.render("msg-reward:100 pièces", TextContext.IDENTITY, true);
        assertEquals("H Tu viens de recevoir la récompense 100 pièces !", plain(reward));
        assertEquals(new Effects.Audio(net.kyori.adventure.key.Key.key("minecraft:block.chain.break"),
                net.kyori.adventure.sound.Sound.Source.MASTER, 1f, .5f), impossible.effects().getFirst());
        assertEquals("minecraft:block.trial_spawner.eject_item", ((Effects.Audio) reward.effects().getFirst()).key().asString());
        assertFalse(reward.miniMessage().contains("<sound:"));
    }

    @Test void placeholdersAndNestedReferencesRemainPureAndDeduplicateAtDispatch() {
        var c = CatalogLoader.parse("""
                child: '<sound:block.chain.break><c:red>{0}</c>'
                parent: '<p:child:{0}>'
                """);
        assertTrue(c.render("child:bonjour", TextContext.IDENTITY, false).effects().isEmpty());
        assertTrue(c.render("parent:bonjour", TextContext.IDENTITY, true).effects().isEmpty());
        var result = new ComponentRenderer(c).replace(Component.text("[[hctkey:child:bonjour]] / [[hctkey:child:bonjour]]"), TextContext.IDENTITY, true);
        assertEquals(1, result.effects().size());
        assertEquals("bonjour / bonjour", PLAIN.serialize(result.component()));
        assertThrows(IllegalArgumentException.class, () -> c.render("child:\"<sound:block.chain.break>\"", TextContext.IDENTITY, true));
    }

    @Test void shortStringsReferencesArgumentsAndEscapes() {
        var c = CatalogLoader.parse("""
                menu: '<gold>{0} — {1}</gold>'
                label: '<p:menu:"Métiers : pêche":"L’ami">'
                nested: '<p:menu:"<p:label>":{0}>'
                escaped: '\\<sound:block.chain.break> '
                apostrophe: "<actionbar:L'ami>Bonjour"
                color: '#E62E39'
                colored: '<c:<p:color>>{0}</c>'
                comparison: '1 < 2 <sound:block.chain.break>Texte'
                """);
        assertEquals("Métiers : pêche — L’ami", plain(c.render("label", TextContext.IDENTITY, true)));
        assertEquals("Métiers : pêche — L’ami — Pêcheur", plain(c.render("nested:Pêcheur", TextContext.IDENTITY, true)));
        var escaped = c.render("escaped", TextContext.IDENTITY, true);
        assertTrue(escaped.effects().isEmpty());
        assertTrue(plain(escaped).contains("<sound:block.chain.break>"));
        assertEquals("L'ami", PLAIN.serialize(((Effects.Actionbar) c.render("apostrophe", TextContext.IDENTITY, true).effects().getFirst()).text()));
        assertEquals("Bonjour", plain(c.render("colored:Bonjour", TextContext.IDENTITY, true)));
        assertEquals(1, c.render("comparison", TextContext.IDENTITY, true).effects().size());
    }

    @Test void soundFormatsAndDefaults() {
        for (String tag : List.of("<sound:block.chain.break>",
                "<sound:'minecraft:block.chain.break'>", "<sound:minecraft:block.chain.break>",
                "<sound:minecraft:block.chain.break:1:1>")) {
            var result = CatalogLoader.parse("message: \"" + tag + "Bonjour\"").render("message", TextContext.IDENTITY, true);
            assertEquals("Bonjour", plain(result));
            assertEquals("minecraft:block.chain.break", ((Effects.Audio) result.effects().getFirst()).key().asString());
        }
        var custom = CatalogLoader.parse("a: \"<sound:'heavencube:ui.click':0.7:1.2>Salut\"");
        assertEquals("heavencube:ui.click", ((Effects.Audio) custom.render("a", TextContext.IDENTITY, true).effects().getFirst()).key().asString());
    }

    @Test void papiTagKeepsAllowlistAndDynamicResolution() {
        var c = CatalogLoader.parse("a: '<gray><papi:player_name></gray>'");
        assertEquals("<gray>%player_name%</gray>", c.render("a", TextContext.IDENTITY, false).miniMessage());
        assertEquals("Noltiii", plain(c.render("a", text -> text.replace("%player_name%", "Noltiii"), false)));
        assertThrows(IllegalArgumentException.class, () -> CatalogLoader.parse("a: '<papi:vault_eco_balance>'"));
        assertThrows(IllegalArgumentException.class, () -> CatalogLoader.parse("a: '<papi:hcextra_tkey_a>'"));
    }

    @Test void effectsUseQuotedTextsParametersAndTicks() {
        var c = CatalogLoader.parse("""
                a: '<sound:entity.player.levelup:0.5:1.2><actionbar:"<gold>Reçu : {0}</gold>"><title:10:70:20:"<gold>Bravo</gold>":"{0}"><bossbar:"{0}":50:YELLOW:SOLID:60>Récompense {0}'
                sub: '<subtitle:10:70:20:"<green>{0}</green>">'
                title: '<title:"<green>{0}</green>">'
                """);
        var a = c.render("a:100 pièces", TextContext.IDENTITY, true);
        assertEquals(4, a.effects().size());
        assertEquals("Récompense 100 pièces", plain(a));
        assertEquals("Reçu : 100 pièces", PLAIN.serialize(((Effects.Actionbar) a.effects().get(1)).text()));
        var title = (Effects.Title) a.effects().get(2);
        assertEquals(java.time.Duration.ofMillis(500), title.fadeIn());
        assertEquals(java.time.Duration.ofMillis(3500), title.stay());
        assertEquals(60, ((Effects.Bar) a.effects().get(3)).ticks());
        assertEquals(.5f, ((Effects.Bar) a.effects().get(3)).progress());
        assertEquals("100 pièces", PLAIN.serialize(((Effects.Subtitle) c.render("sub:100 pièces", TextContext.IDENTITY, true).effects().getFirst()).text()));
        assertEquals("Bravo", PLAIN.serialize(((Effects.Title) c.render("title:Bravo", TextContext.IDENTITY, true).effects().getFirst()).title()));
        assertTrue(c.render("a:100 pièces", TextContext.IDENTITY, false).effects().isEmpty());
    }

    @Test void malformedTagsAndConflictingDefinitionsRejectTheCandidate() {
        for (String yaml : List.of("a: '<sound:block.chain.break:NaN:1>'", "a: '<sound:block.chain.break:1:0>'",
                "a: '<sound:block.chain.break:9:1>'", "a: '<sound:block.chain.break'", "a: '<sound>'",
                "a: '<sound:ENTITY_EXPERIENCE_ORB_PICKUP>'",
                "a: '<sound:block.chain.break><sound:block.chain.break>'", "a: '<p:missing>'", "a: '<p:a>'",
                "a: '<p:b>'\nb: '{0}'", "a: '<title:10:70:\"text\">'", "a: '<bossbar:text:1:RED:SOLID:0>'",
                "a: {value: x, text: y}", "a: {value: x, type: invalid}", "a: {type: colored_text}",
                "a: {text: '<sound:block.chain.break>', sound: {key: minecraft:block.chain.break}}",
                "a: {actionbar: '<sound:block.chain.break>'}", "theme: {colors: {primary: '<sound:block.chain.break>'}}"))
            assertThrows(IllegalArgumentException.class, () -> CatalogLoader.parse(yaml), yaml);
    }

    @Test void inlineReferenceExpansionRemainsBoundedAndReloadTransactional() {
        var yaml = new StringBuilder("a0: x\n");
        for (int i = 1; i < 20; i++) yaml.append("a").append(i).append(": '<p:a").append(i - 1).append("><p:a").append(i - 1).append(">'\n");
        assertThrows(IllegalArgumentException.class, () -> CatalogLoader.parse(yaml.toString()));
        var store = new CatalogStore(CatalogLoader.parse("a: '<sound:block.chain.break>Avant'"));
        var before = store.current();
        assertThrows(IllegalArgumentException.class, () -> store.reload(() -> CatalogLoader.parse("a: '<sound:block.chain.break:NaN:1>Après'")));
        assertSame(before, store.current());
        assertEquals("Avant", plain(store.current().render("a", TextContext.IDENTITY, false)));
    }
}
