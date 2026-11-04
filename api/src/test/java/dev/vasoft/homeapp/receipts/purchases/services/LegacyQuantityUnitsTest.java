package dev.vasoft.homeapp.receipts.purchases.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.vasoft.homeapp.receipts.purchases.model.entities.QuantityUnit;
import dev.vasoft.homeapp.receipts.purchases.services.LegacyQuantityUnits.Reason;
import dev.vasoft.homeapp.receipts.purchases.services.LegacyQuantityUnits.Row;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LegacyQuantityUnitsTest {

    @TempDir
    Path dir;

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Лук (цена за кг)                  | KILOGRAM | PER_KG_IN_NAME",
        "банани на кг                      | KILOGRAM | PER_KG_IN_NAME",
        "Домати - цена за 1 кг             | KILOGRAM | PER_KG_IN_NAME",
        "Сирене /кг                        | KILOGRAM | PER_KG_IN_NAME",
        "Somat Гел двоен - цена на литър   | LITER    | PER_LITRE_IN_NAME",
        "Авокадо (на брой)                 | PIECE    | PER_PIECE_IN_NAME",
        "Яйца М - цена за бройка           | PIECE    | PER_PIECE_IN_NAME",
        "Medix соли 1.5кг                  | PIECE    | PACK_SIZE_IN_NAME",
        "Броколи 500гр                     | PIECE    | PACK_SIZE_IN_NAME",
        "Лалета 7 бр.                      | PIECE    | PACK_SIZE_IN_NAME",
        "Coca Cola 1,5 л                   | PIECE    | PACK_SIZE_IN_NAME",
        "Прясно мляко                      | PIECE    | NO_MARKER",
        // "на л" must be a word of its own, not the start of one
        "Салата на лук                     | PIECE    | NO_MARKER",
    })
    void guessesTheUnitFromTheName(String name, QuantityUnit unit, Reason reason) {
        assertThat(LegacyQuantityUnits.guess(name))
            .isEqualTo(new LegacyQuantityUnits.Guess(unit, reason));
    }

    @Test
    void aMissingNameIsAPiece() {
        assertThat(LegacyQuantityUnits.guess(null).unit()).isEqualTo(QuantityUnit.PIECE);
    }

    @Test
    void theWrittenFileReadsBackAsProductIdToUnit() throws Exception {
        ObjectId onions = new ObjectId();
        ObjectId milk = new ObjectId();
        Path file = dir.resolve("nested/units.proposed.tsv");

        LegacyQuantityUnits.write(file, List.of(
            new Row(milk, "Прясно мляко", 12, QuantityUnit.PIECE, Reason.NO_MARKER),
            new Row(onions, "Лук (цена за кг)", 7, QuantityUnit.KILOGRAM, Reason.PER_KG_IN_NAME)),
            "units.tsv");

        List<String> lines = Files.readAllLines(file);
        assertThat(lines).anyMatch(l -> l.contains("save this file as units.tsv"));
        List<String> rows = lines.stream().filter(l -> !l.startsWith("#")).toList();
        // Weighed units first: those are the rows worth checking
        assertThat(rows.getFirst()).startsWith("KILOGRAM\t7\t" + onions.toHexString()
            + "\tЛук (цена за кг)\t");
        assertThat(LegacyQuantityUnits.read(file))
            .containsExactlyInAnyOrderEntriesOf(Map.of(onions, QuantityUnit.KILOGRAM,
                milk, QuantityUnit.PIECE));
    }

    @Test
    void aCorrectedUnitIsReadInAnyCase() throws Exception {
        ObjectId id = new ObjectId();
        Path file = dir.resolve("units.tsv");
        Files.writeString(file, "# reviewed\n\nliter\t3\t" + id.toHexString() + "\tSomat\n");

        assertThat(LegacyQuantityUnits.read(file)).containsExactly(Map.entry(id, QuantityUnit.LITER));
    }

    @Test
    void anUnknownUnitIsRefusedWithItsLine() throws Exception {
        Path file = dir.resolve("units.tsv");
        Files.writeString(file, "# reviewed\nKG\t3\t" + new ObjectId().toHexString() + "\tЛук\n");

        assertThatThrownBy(() -> LegacyQuantityUnits.read(file))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("line 2")
            .hasMessageContaining("unknown unit \"KG\"");
    }

    @Test
    void aProductListedTwiceIsRefused() throws Exception {
        String id = new ObjectId().toHexString();
        Path file = dir.resolve("units.tsv");
        Files.writeString(file, "PIECE\t1\t" + id + "\ta\nKILOGRAM\t1\t" + id + "\ta\n");

        assertThatThrownBy(() -> LegacyQuantityUnits.read(file))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("listed twice");
    }

    @Test
    void theProposalSitsNextToTheReviewedFile() {
        assertThat(LegacyQuantityMigration.proposalPathFor(Path.of(".migrated/units.tsv")))
            .isEqualTo(Path.of(".migrated/units.proposed.tsv"));
    }
}
