package fr.noltox.hcplugins.translationkey;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ExpressionTest {
    @Test void dialogFieldsPreserveTheirValues() {
        var values = List.of("L'ami", "a:b", "Il dit \"oui\"", "[[hctkey:ui.close]]", "");
        assertEquals(values, Expression.parse(AdminDialog.expression("menu.title", values)).arguments());
    }
    @Test void noArguments() { assertEquals(new Expression("ui.close", List.of()), Expression.parse("ui.close")); }
    @Test void quotingEscapingAndUnicode() {
        assertEquals(List.of("Métiers", "L'ami", "a:b", "", "c:d", "Il dit \"oui\""),
                Expression.parse("menu.title:Métiers:L'ami:\"a:b\":\"\":c\\:d:\"Il dit \\\"oui\\\"\"").arguments());
    }
    @Test void preservesNestedExpressionsAndMiniMessage() {
        assertEquals(List.of("[[hctkey:child:a:b]]", "<gradient:red:blue>Texte</gradient>", "%player_name%"),
                Expression.parse("menu:[[hctkey:child:a:b]]:<gradient:red:blue>Texte</gradient>:%player_name%").arguments());
    }
    @Test void emptyAndSpacedArguments() {
        assertEquals(List.of("", " deux mots ", ""), Expression.parse("key:: deux mots :").arguments());
    }
    @Test void malformedIsRejected() {
        for (String value : List.of("", "a b", "key:\"abc", "key:[[hctkey:x", "key:abc\\", "key:\"a\"bad"))
            assertThrows(IllegalArgumentException.class, () -> Expression.parse(value), value);
    }
    @Test void longAndDeepInputRejected() {
        assertThrows(IllegalArgumentException.class, () -> Expression.parse("x:" + "a".repeat(8193)));
        assertThrows(IllegalArgumentException.class, () -> Template.compile("[[hctkey:a:".repeat(40) + "x" + "]]".repeat(40)));
    }
}
