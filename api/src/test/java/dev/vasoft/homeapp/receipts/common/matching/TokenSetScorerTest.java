package dev.vasoft.homeapp.receipts.common.matching;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TokenSetScorerTest {

    private final TokenSetScorer scorer = new TokenSetScorer();

    private double score(String query, String candidate) {
        return scorer.score(query, candidate).value();
    }

    @Test
    void identicalNamesScoreOne() {
        assertThat(score("Прясно мляко", "Прясно мляко")).isEqualTo(1.0);
    }

    @Test
    void wordOrderDoesNotMatter() {
        // ROADMAP D12: the whole-string matcher missed this on word order alone
        assertThat(score("мляко прясно", "Прясно мляко")).isEqualTo(1.0);
    }

    @Test
    void caseAccentsAndPunctuationAreFolded() {
        assertThat(score("CAFÉ", "cafe")).isEqualTo(1.0);
        assertThat(score("coca cola", "Coca-Cola")).isEqualTo(1.0);
        assertThat(score("ПРЯСНО МЛЯКО", "прясно мляко")).isEqualTo(1.0);
    }

    @Test
    void repeatedWordsCountOnce() {
        assertThat(score("мляко мляко", "мляко")).isEqualTo(1.0);
    }

    @Test
    void sharingOnlyAConnectingWordIsNoMatch() {
        // ROADMAP D12: the old matcher confidently paired these
        assertThat(score("кафе на зърна", "карфиол на брой")).isZero();
    }

    @Test
    void aQueryOfOnlyConnectingWordsStillMatchesThem() {
        assertThat(score("на", "Лук на кг")).isGreaterThan(0.5);
    }

    @Test
    void aHalfTypedWordMatchesAsAPrefix() {
        assertThat(score("мля", "Мляко")).isGreaterThan(0.9);
        assertThat(score("прясно мл", "Прясно мляко")).isGreaterThan(0.9);
    }

    @Test
    void aTypoStillMatchesButBelowTheCorrectSpelling() {
        double typo = score("мляка", "Мляко");
        assertThat(typo).isGreaterThan(0.7).isLessThan(score("мляко", "Мляко"));
        assertThat(score("kasakval", "Кашкавал")).isGreaterThan(0.7);
    }

    @Test
    void aTypoInAHalfTypedWordStillMatches() {
        assertThat(score("кашкв", "Кашкавал Витоша")).isGreaterThan(0.6);
    }

    @Test
    void shortWordsMustMatchExactlyOrAsAPrefix() {
        // One edit in three letters is a different word
        assertThat(score("лук", "Лак за коса")).isZero();
        assertThat(score("лук", "Лук (цена за кг)")).isGreaterThan(0.85);
    }

    @Test
    void latinInputFindsCyrillicNames() {
        assertThat(score("mlyako", "Мляко")).isEqualTo(1.0);
        assertThat(score("kashkaval", "Кашкавал")).isEqualTo(1.0);
        assertThat(score("banani", "банани на кг")).isGreaterThan(0.9);
    }

    @Test
    void cyrillicInputFindsLatinNames() {
        assertThat(score("нескафе", "Nescafe Classic")).isGreaterThan(0.7);
        assertThat(score("милка", "Milka")).isEqualTo(1.0);
    }

    @Test
    void mixedScriptInputMatchesWordByWord() {
        assertThat(score("Milka шоколад", "Шоколад Милка")).isEqualTo(1.0);
    }

    @Test
    void theOfficialTransliterationIsUsed() {
        assertThat(Transliteration.toLatin("щъркел")).isEqualTo("shtarkel");
        assertThat(Transliteration.toLatin("юния")).isEqualTo("yunia");
        assertThat(Transliteration.toLatin("жица")).isEqualTo("zhitsa");
        assertThat(Transliteration.toLatin("milk")).isEqualTo("milk");
    }

    @Test
    void anExactProductOutranksOneWithExtraWords() {
        assertThat(score("мляко", "Мляко"))
            .isGreaterThan(score("мляко", "Мляко краве 3% Верея"));
    }

    @Test
    void everyQueryWordMattersMoreThanEveryCandidateWord() {
        // Half the query matched is worse than all of it matched in a longer name
        assertThat(score("кисело мляко", "Прясно мляко"))
            .isLessThan(score("кисело мляко", "Кисело мляко Верея 2%"));
    }

    @Test
    void unrelatedNamesScoreZero() {
        assertThat(score("хляб", "Кашкавал")).isZero();
    }

    @Test
    void emptyInputScoresZero() {
        assertThat(score("", "Мляко")).isZero();
        assertThat(score("  -- ", "Мляко")).isZero();
        assertThat(score("мляко", "")).isZero();
        assertThat(score("мляко", null)).isZero();
        assertThat(scorer.query(" ... ").isEmpty()).isTrue();
    }

    @Test
    void bestScoreTakesTheBestOfSeveralNames() {
        TokenSetScorer.Query q = scorer.query("кока кола");
        assertThat(q.bestScore(List.of("Coca-Cola Zero", "Кока Кола")).value()).isEqualTo(1.0);
        assertThat(q.bestScore(null).value()).isZero();
        assertThat(q.bestScore(List.of()).value()).isZero();
    }

    @Test
    void scoresStayBetweenZeroAndOne() {
        List<String> names = List.of("Прясно мляко", "Кисело мляко Верея 2%", "Лук (цена за кг)",
            "Coca-Cola", "Яйца М - цена за бройка", "Somat Гел двоен - цена на литър");
        List<String> queries = List.of("м", "мляко", "lyk", "cola zero", "яйца 10", "гел");
        for (String q : queries) {
            for (String n : names) {
                TokenSetScorer.Score s = scorer.score(q, n);
                assertThat(s.value()).as(q + " / " + n).isBetween(0.0, 1.0);
                assertThat(s.queryCoverage()).isBetween(0.0, 1.0);
                assertThat(s.candidateCoverage()).isBetween(0.0, 1.0);
            }
        }
    }
}
