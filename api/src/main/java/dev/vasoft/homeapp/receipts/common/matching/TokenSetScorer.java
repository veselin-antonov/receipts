package dev.vasoft.homeapp.receipts.common.matching;

import dev.vasoft.homeapp.receipts.scanning.services.NormalizationService;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Scores how well a piece of text names a product, word by word rather than as
 * one string, so that word order does not matter: {@code "мляко прясно"} finds
 * {@code "Прясно мляко"} (ROADMAP D12, SPEC F2.1).
 *
 * <p>Both sides are folded the way product names are normalized everywhere
 * ({@link NormalizationService}): lower case, accents and other combining marks
 * removed, punctuation turned into spaces. Each side is then split into a set of
 * words. Every query word is matched to its best word in the candidate:
 * <ul>
 *   <li>the same word scores 1;</li>
 *   <li>a prefix scores {@value #PREFIX_SCORE}, so a half-typed word matches
 *       while the user is still typing;</li>
 *   <li>a near miss scores its edit similarity, if that is at least
 *       {@value #MIN_WORD_SIMILARITY}; words of three letters or fewer must match
 *       exactly or as a prefix, since one edit changes them too much.</li>
 * </ul>
 * The query's words are averaged into {@link Score#queryCoverage()}, and the
 * share of the candidate's words that a query word claimed into
 * {@link Score#candidateCoverage()}. {@link Score#value()} weighs the first
 * {@value #QUERY_WEIGHT} to 1, so that "мляко" finds every milk, but plain
 * {@code Мляко} ranks above {@code Мляко краве 3% Верея}.
 *
 * <p>Bulgarian is also compared in Latin letters, using the official
 * transliteration (Закон за транслитерацията), so {@code "mlyako"} finds
 * {@code Мляко}, and {@code "Нескафе"} finds {@code Nescafe Classic} (as a near
 * miss: {@code neskafe} against {@code nescafe}). The better of the two
 * comparisons counts.
 *
 * <p>Connecting words ({@code на}, {@code за}, {@code и}, …) are left out of
 * both sides unless the query is nothing else, so {@code "кафе на зърна"} gets
 * no credit from {@code "карфиол на брой"} sharing its {@code "на"}.
 *
 * <p>Stateless and thread-safe. It holds no Spring dependencies on purpose: the
 * product search uses it now, and the receipt matcher is meant to switch to it
 * so that both score names by one rule.
 */
public final class TokenSetScorer {

    static final double PREFIX_SCORE = 0.9;
    static final double MIN_WORD_SIMILARITY = 0.7;
    static final double QUERY_WEIGHT = 0.85;
    private static final int MIN_FUZZY_WORD_LENGTH = 4;

    private static final Set<String> STOP_WORDS = Set.of(
        "на", "за", "и", "с", "със", "от", "в", "във", "по", "до",
        "na", "za", "i", "s", "sas", "ot", "v", "vav", "po", "do",
        "the", "of", "and", "with", "a");

    /** A score in [0, 1] together with the two coverages it is made of. */
    public record Score(double value, double queryCoverage, double candidateCoverage) {

        public static final Score NONE = new Score(0, 0, 0);
    }

    /**
     * Prepares a query once, so that it can be scored against many candidates
     * without being folded again for each one.
     */
    public Query query(String text) {
        return new Query(Words.of(text));
    }

    /** Scores {@code text} as a name for {@code candidate}. */
    public Score score(String text, String candidate) {
        return query(text).score(candidate);
    }

    /** A folded query, ready to be scored. */
    public static final class Query {

        private final Words words;

        private Query(Words words) {
            this.words = words;
        }

        /** True when the query has no letters or digits at all. */
        public boolean isEmpty() {
            return words.native_().isEmpty();
        }

        public Score score(String candidate) {
            if (isEmpty() || candidate == null) {
                return Score.NONE;
            }
            Words other = Words.of(candidate);
            if (other.native_().isEmpty()) {
                return Score.NONE;
            }
            Score nativeScore = compare(words.native_(), other.native_());
            Score latinScore = compare(words.latin(), other.latin());
            return latinScore.value() > nativeScore.value() ? latinScore : nativeScore;
        }

        /** The best score over several names for one product, such as its aliases. */
        public Score bestScore(Collection<String> candidates) {
            Score best = Score.NONE;
            if (candidates == null) {
                return best;
            }
            for (String candidate : candidates) {
                Score s = score(candidate);
                if (s.value() > best.value()) {
                    best = s;
                }
            }
            return best;
        }
    }

    private static Score compare(List<String> query, List<String> candidate) {
        List<String> q = withoutStopWords(query);
        List<String> c = withoutStopWords(candidate);
        if (q.isEmpty()) {
            // The query is only connecting words: compare them as they are
            q = query;
            c = candidate;
        }
        if (c.isEmpty()) {
            return Score.NONE;
        }

        boolean[] claimed = new boolean[c.size()];
        double sum = 0;
        for (String word : q) {
            double best = 0;
            int bestIndex = -1;
            for (int i = 0; i < c.size(); i++) {
                double s = wordScore(word, c.get(i));
                if (s > best) {
                    best = s;
                    bestIndex = i;
                }
            }
            if (bestIndex >= 0) {
                claimed[bestIndex] = true;
            }
            sum += best;
        }
        int claimedCount = 0;
        for (boolean b : claimed) {
            if (b) {
                claimedCount++;
            }
        }

        double queryCoverage = sum / q.size();
        double candidateCoverage = (double) claimedCount / c.size();
        if (queryCoverage == 0) {
            return Score.NONE;
        }
        double value = QUERY_WEIGHT * queryCoverage + (1 - QUERY_WEIGHT) * candidateCoverage;
        return new Score(value, queryCoverage, candidateCoverage);
    }

    static double wordScore(String query, String candidate) {
        if (query.equals(candidate)) {
            return 1.0;
        }
        if (candidate.startsWith(query)) {
            return PREFIX_SCORE;
        }
        if (query.length() < MIN_FUZZY_WORD_LENGTH) {
            return 0;
        }
        double similarity = similarity(query, candidate);
        if (candidate.length() > query.length()) {
            // A half-typed word with a typo in it: compare against the candidate's
            // start, and rank it below a clean prefix
            double prefixSimilarity = similarity(query,
                candidate.substring(0, query.length())) * PREFIX_SCORE;
            similarity = Math.max(similarity, prefixSimilarity);
        }
        return similarity >= MIN_WORD_SIMILARITY ? similarity : 0;
    }

    private static List<String> withoutStopWords(List<String> words) {
        return words.stream().filter(w -> !STOP_WORDS.contains(w)).toList();
    }

    /** Edit similarity: 1 minus the Levenshtein distance over the longer length. */
    static double similarity(String left, String right) {
        int maxLength = Math.max(left.length(), right.length());
        if (maxLength == 0) {
            return 1.0;
        }
        return 1.0 - (double) levenshtein(left, right) / maxLength;
    }

    private static int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1),
                    previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    /** One text's distinct words, as written and in Latin letters. */
    private record Words(List<String> native_, List<String> latin) {

        static Words of(String text) {
            String folded = NormalizationService.normalizeProductName(text);
            if (folded.isEmpty()) {
                return new Words(List.of(), List.of());
            }
            Set<String> nativeWords = new LinkedHashSet<>(List.of(folded.split(" ")));
            Set<String> latinWords = new LinkedHashSet<>();
            for (String word : nativeWords) {
                latinWords.add(Transliteration.toLatin(word));
            }
            return new Words(List.copyOf(nativeWords), List.copyOf(latinWords));
        }
    }
}
