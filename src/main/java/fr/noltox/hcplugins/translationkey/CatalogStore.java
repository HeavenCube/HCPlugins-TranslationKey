package fr.noltox.hcplugins.translationkey;

import java.util.Objects;
import java.util.function.Supplier;

final class CatalogStore {
    private volatile Catalog current;
    CatalogStore(Catalog initial) { current = Objects.requireNonNull(initial); }
    Catalog current() { return current; }
    void reload(Supplier<Catalog> loader) {
        Catalog candidate = Objects.requireNonNull(loader.get());
        current = candidate;
    }
}
