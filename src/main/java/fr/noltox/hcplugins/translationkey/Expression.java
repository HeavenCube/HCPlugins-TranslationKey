package fr.noltox.hcplugins.translationkey;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

record Expression(String key, List<String> arguments) {
    private static final Pattern KEY = Pattern.compile("[a-z][a-z0-9-]*(?:\\.[a-z0-9][a-z0-9-]*)*");
    Expression { arguments = List.copyOf(arguments); }
    static boolean validKey(String key) { return KEY.matcher(key).matches(); }

    static Expression parse(String input) {
        if (input.length() > Limits.EXPRESSION) throw new IllegalArgumentException("Expression trop longue.");
        var parts = new ArrayList<String>();
        var part = new StringBuilder();
        char quote = 0;
        boolean quotedEnd = false;
        int tags = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\\') {
                if (++i == input.length()) throw new IllegalArgumentException("Échappement incomplet.");
                char next = input.charAt(i);
                if (":\\\"'".indexOf(next) >= 0) part.append(next);
                else part.append('\\').append(next);
            } else if (quote != 0) {
                if (c == quote) { quote = 0; quotedEnd = true; }
                else part.append(c);
            } else if (input.startsWith(Markers.OPEN, i)) {
                int end = Markers.end(input, i);
                part.append(input, i, end);
                i = end - 1;
            } else if ((c == '"' || c == '\'') && part.isEmpty() && !parts.isEmpty()) {
                quote = c;
            } else if (c == ':' && tags == 0) {
                parts.add(part.toString()); part.setLength(0); quotedEnd = false;
                if (parts.size() > Limits.ARGUMENTS) throw new IllegalArgumentException("Trop d'arguments.");
            } else {
                if (quotedEnd) throw new IllegalArgumentException("Texte après un argument fermé par une quote.");
                if (c == '<') tags++;
                else if (c == '>' && tags > 0) tags--;
                part.append(c);
            }
        }
        if (quote != 0) throw new IllegalArgumentException("Quote non fermée.");
        parts.add(part.toString());
        String key = parts.removeFirst();
        if (!validKey(key)) throw new IllegalArgumentException("Identifiant invalide : " + key);
        return new Expression(key, parts);
    }
}
