package fr.noltox.hcplugins.translationkey;

import org.junit.jupiter.api.Test;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.event.*;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import static org.junit.jupiter.api.Assertions.*;

class ComponentRendererTest {
    @Test void totalOutputIsBoundedAcrossMultipleMarkers() {
        var big = CatalogLoader.parse("large: '" + "a".repeat(20000) + "'");
        assertThrows(IllegalArgumentException.class, () -> new ComponentRenderer(big).replace(
                Component.text("[[hctkey:large]][[hctkey:large]]"), TextContext.IDENTITY, false));
    }
    private final Catalog catalog = CatalogLoader.parse("a: '<gold>A</gold>'\nb: 'B {0}'");
    @Test void untouchedComponentsKeepIdentity() {
        Component input = Component.text("Aucun marqueur").append(Component.text(" !"));
        assertFalse(ComponentRenderer.contains(input));
        var result = new ComponentRenderer(catalog).replace(input, TextContext.IDENTITY, true);
        assertSame(input, result.component()); assertFalse(result.changed());
    }
    @Test void replacesAllReferencesAndPreservesParentStyleEventsAndChildren() {
        Component input = Component.text("Avant [[hctkey:a]] / [[hctkey:b:\"x:y\"]] après", NamedTextColor.BLUE)
                .clickEvent(ClickEvent.suggestCommand("/hello"))
                .hoverEvent(HoverEvent.showText(Component.text("survol")))
                .append(Component.text(" enfant [[hctkey:a]]"));
        var result = new ComponentRenderer(catalog).replace(input, TextContext.IDENTITY, true);
        assertEquals("Avant A / B x:y après enfant A", PlainTextComponentSerializer.plainText().serialize(result.component()));
        assertEquals(input.style(), result.component().style());
        assertTrue(result.changed());
    }
    @Test void nestedArgumentsAreSingleTopLevelMarker() {
        var result = new ComponentRenderer(catalog).replace(Component.text("[[hctkey:b:[[hctkey:a]]]]"), TextContext.IDENTITY, true);
        assertEquals("B A", PlainTextComponentSerializer.plainText().serialize(result.component()));
    }
    @Test void malformedOrUnknownMarkerFailsWithoutMutatingInput() {
        Component original = Component.text("[[hctkey:absent]]");
        assertThrows(IllegalArgumentException.class, () -> new ComponentRenderer(catalog).replace(original, TextContext.IDENTITY, true));
        assertEquals("[[hctkey:absent]]", ((TextComponent) original).content());
    }
}
