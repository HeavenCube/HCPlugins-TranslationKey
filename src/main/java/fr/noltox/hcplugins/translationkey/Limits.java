package fr.noltox.hcplugins.translationkey;

final class Limits {
    static final int DEPTH = 32, ARGUMENTS = 16, EXPRESSION = 8192, OUTPUT = 32768, OPERATIONS = 1024;
    private Limits() {}
    static String text(String value) {
        if (value.length() > OUTPUT) throw new IllegalArgumentException("Contenu supérieur à " + OUTPUT + " caractères.");
        return value;
    }
    static void depth(int depth) {
        if (depth > DEPTH) throw new IllegalArgumentException("Profondeur supérieure à " + DEPTH + ".");
    }
    static final class Budget {
        final TextContext context;
        Budget() { this(TextContext.IDENTITY); }
        Budget(TextContext context) { this.context = context; }
        private int remaining = OPERATIONS;
        private int characters;
        void characters(int count) {
            characters += count;
            if (characters > OUTPUT) throw new IllegalArgumentException("Composant résolu trop long.");
        }
        void step(int depth) {
            Limits.depth(depth);
            if (--remaining < 0) throw new IllegalArgumentException("Résolution trop complexe (limite " + OPERATIONS + ").");
        }
    }
}
