package fr.noltox.hcplugins.translationkey;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CatalogLimitsTest {
    @Test void oversizedOutputIsBoundedEvenWithExponentialReferences() {
        StringBuilder yaml = new StringBuilder("a0: x\n");
        for (int i = 1; i < 20; i++) yaml.append("a").append(i).append(": '[[hctkey:a").append(i - 1)
                .append("]][[hctkey:a").append(i - 1).append("]]'\n");
        assertThrows(IllegalArgumentException.class, () -> CatalogLoader.parse(yaml.toString()));
    }
    @Test void referenceDepthIsBoundedBeforeStackOverflow() {
        var yaml = new StringBuilder("a0: x\n");
        for (int i = 1; i < 100; i++) yaml.append("a").append(i).append(": '[[hctkey:a").append(i - 1).append("]]'\n");
        assertThrows(IllegalArgumentException.class, () -> CatalogLoader.parse(yaml.toString()));
    }
    @Test void duplicateFlattenedKeysAndYamlAliasesRejected() {
        assertThrows(IllegalArgumentException.class, () -> CatalogLoader.parse("a.b: x\na: {b: y}"));
        assertThrows(IllegalArgumentException.class, () -> CatalogLoader.parse("a: &a {b: c}\nd: *a"));
    }
    @Test void effectfulOrRecursivePapiCannotBeAuthorized() {
        for (String name : List.of("hcextra_checkitem_give_mat:STONE", "hcextra_checkitem_remove_mat:STONE",
                "hcextra_tkey_a", "checkitem_give_mat:STONE"))
            assertThrows(IllegalArgumentException.class, () -> CatalogLoader.parse(
                    "settings:\n  allowed-placeholders: ['" + name + "']\na: '%" + name + "%'"));
    }
    @Test void externalPlaceholderRequiresAnExplicitAllowance() {
        assertThrows(IllegalArgumentException.class, () -> CatalogLoader.parse("a: '%vault_eco_balance%'"));
        var c = CatalogLoader.parse("settings: {allowed-placeholders: [vault_eco_balance]}\na: '%vault_eco_balance%'");
        assertEquals("%vault_eco_balance%", c.render("a", TextContext.IDENTITY, false).miniMessage());
    }
    @Test void arraysAndWrongEnrichedTypesAreRejected() {
        for (String value : List.of("a: [x, y]", "a: {actionbar: 10}", "a: {sound: {key: minecraft:a, unknown: a}}",
                "a: {bossbar: {text: a, duration: 0s}}", "a: {title: {title: a, stay: 301s}}"))
            assertThrows(IllegalArgumentException.class, () -> CatalogLoader.parse(value), value);
    }
}
