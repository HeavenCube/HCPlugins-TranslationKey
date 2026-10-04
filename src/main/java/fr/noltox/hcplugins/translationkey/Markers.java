package fr.noltox.hcplugins.translationkey;

final class Markers {
    static final String OPEN = "[[hctkey:";
    private Markers() {}
    /** Exclusive end; nested markers, escaped quotes and quoted ]] are preserved. */
    static int end(String text, int start) {
        int depth = 1;
        char quote = 0;
        boolean argumentStart = true;
        for (int i = start + OPEN.length(); i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\') { i++; continue; }
            if (quote != 0) { if (c == quote) quote = 0; continue; }
            if ((c == '"' || c == '\'') && argumentStart) { quote = c; argumentStart = false; continue; }
            if (text.startsWith(OPEN, i)) {
                Limits.depth(++depth); i += OPEN.length() - 1; argumentStart = true;
            } else if (text.startsWith("]]", i)) {
                if (--depth == 0) return i + 2;
                i++; argumentStart = false;
            } else argumentStart = c == ':';
        }
        throw new IllegalArgumentException("Marqueur HCTranslationKey non fermé à la position " + start + ".");
    }
}
