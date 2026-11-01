package dev.vasoft.homeapp.receipts.common.matching;

import java.util.Map;

/**
 * Bulgarian Cyrillic to Latin letters, by the official system (Закон за
 * транслитерацията, 2009), for text already folded to lower case. Anything that
 * is not Bulgarian Cyrillic passes through unchanged, so Latin input stays as it
 * is and mixed input is handled letter by letter.
 */
final class Transliteration {

    private static final Map<Character, String> LETTERS = Map.ofEntries(
        Map.entry('а', "a"), Map.entry('б', "b"), Map.entry('в', "v"),
        Map.entry('г', "g"), Map.entry('д', "d"), Map.entry('е', "e"),
        Map.entry('ж', "zh"), Map.entry('з', "z"), Map.entry('и', "i"),
        Map.entry('й', "y"), Map.entry('к', "k"), Map.entry('л', "l"),
        Map.entry('м', "m"), Map.entry('н', "n"), Map.entry('о', "o"),
        Map.entry('п', "p"), Map.entry('р', "r"), Map.entry('с', "s"),
        Map.entry('т', "t"), Map.entry('у', "u"), Map.entry('ф', "f"),
        Map.entry('х', "h"), Map.entry('ц', "ts"), Map.entry('ч', "ch"),
        Map.entry('ш', "sh"), Map.entry('щ', "sht"), Map.entry('ъ', "a"),
        Map.entry('ь', "y"), Map.entry('ю', "yu"), Map.entry('я', "ya"));

    private Transliteration() {
    }

    static String toLatin(String word) {
        // The law's one positional rule: a word-final "ия" is "ia", not "iya"
        boolean finalIa = word.endsWith("ия");
        String stem = finalIa ? word.substring(0, word.length() - 2) : word;

        StringBuilder out = new StringBuilder(word.length() + 4);
        for (int i = 0; i < stem.length(); i++) {
            char c = stem.charAt(i);
            String latin = LETTERS.get(c);
            if (latin != null) {
                out.append(latin);
            } else {
                out.append(c);
            }
        }
        if (finalIa) {
            out.append("ia");
        }
        return out.toString();
    }
}
