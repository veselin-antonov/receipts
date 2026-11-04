package dev.vasoft.homeapp.receipts.purchases.services;

import dev.vasoft.homeapp.receipts.purchases.model.entities.QuantityUnit;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.bson.types.ObjectId;

/**
 * The unit each legacy product is sold in, for {@link LegacyQuantityMigration}:
 * how it is guessed from the product's name, and the reviewable file it is
 * written to and read back from.
 *
 * <p>The old app had no quantity, so its catalog put the unit in the name:
 * {@code Лук (цена за кг)}, {@code банани на кг},
 * {@code Somat Гел двоен - цена на литър}. A name that says "per kg" or "per
 * litre" is that unit; everything else is a piece, including names that only
 * state a pack size, such as {@code Medix соли 1.5кг} or {@code Броколи 500гр}:
 * that was the price of one pack. The guess is only a proposal. Nothing is
 * written until a person has reviewed the file.
 *
 * <p>The file is tab-separated, one product per line:
 * {@code unit, legacy purchases, product id, product name, reason}. Only the
 * first and third columns are read back; lines starting with {@code #} are
 * comments. It holds product names, which are personal data, so it lives
 * outside git.
 */
public final class LegacyQuantityUnits {

    /** Why a unit was proposed, shown in the file so the review can skim it. */
    public enum Reason {
        PER_KG_IN_NAME("name says per kg"),
        PER_LITRE_IN_NAME("name says per litre"),
        PER_PIECE_IN_NAME("name says per piece"),
        PACK_SIZE_IN_NAME("name states a pack size; one pack = one piece"),
        NO_MARKER("no unit in name");

        final String text;

        Reason(String text) {
            this.text = text;
        }
    }

    /** A proposed or approved unit for one product. */
    public record Row(ObjectId productId, String name, long purchases, QuantityUnit unit,
                      Reason reason) {
    }

    /** One unit and why, from a name alone. */
    public record Guess(QuantityUnit unit, Reason reason) {
    }

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        | Pattern.UNICODE_CHARACTER_CLASS;

    /** "за кг", "на кг", "цена за 1 кг", "/кг", "per kg". */
    private static final Pattern PER_KG = Pattern.compile(
        "(?:за|на|per|/)\\s*(?:1\\s*)?(?:кг|kg|килограм)(?!\\p{L})", FLAGS);

    /** "цена на литър", "за л", "/л", "per l". */
    private static final Pattern PER_LITRE = Pattern.compile(
        "(?:за|на|per|/)\\s*(?:1\\s*)?(?:литър|литра|л|l)(?!\\p{L})", FLAGS);

    /** "на брой", "цена за бройка", "за бр." */
    private static final Pattern PER_PIECE = Pattern.compile(
        "(?:за|на)\\s*(?:1\\s*)?(?:бройка|брой|бр)(?!\\p{L})", FLAGS);

    /** "1.5кг", "500гр", "330 мл", "2л", "10 бр." */
    private static final Pattern PACK_SIZE = Pattern.compile(
        "\\d+(?:[.,]\\d+)?\\s*(?:кг|гр|г|мл|л|бр|kg|g|ml|l)(?!\\p{L})", FLAGS);

    static final String HEADER = """
        # Legacy quantity migration: the unit each product's old purchases were priced in.
        #
        # Every purchase recorded before quantities existed becomes quantity 1 in
        # its product's unit, so its stored price stays the unit price, and is
        # marked quantityAssumed. Product names are not changed.
        #
        # Review the first column. KILOGRAM: the price was per kg. LITER: per litre.
        # PIECE: per piece or per pack. GRAM and MILLILITER are accepted too.
        # Only the unit and the product id are read back; the rest is for you.
        #
        # To apply: save this file as %s next to it, then restart the api.
        #
        # unit\tpurchases\tproduct id\tproduct name\treason
        """;

    private LegacyQuantityUnits() {
    }

    /** Proposes a unit from a product name. */
    public static Guess guess(String name) {
        String n = name == null ? "" : name;
        if (PER_KG.matcher(n).find()) {
            return new Guess(QuantityUnit.KILOGRAM, Reason.PER_KG_IN_NAME);
        }
        if (PER_LITRE.matcher(n).find()) {
            return new Guess(QuantityUnit.LITER, Reason.PER_LITRE_IN_NAME);
        }
        if (PER_PIECE.matcher(n).find()) {
            return new Guess(QuantityUnit.PIECE, Reason.PER_PIECE_IN_NAME);
        }
        if (PACK_SIZE.matcher(n).find()) {
            return new Guess(QuantityUnit.PIECE, Reason.PACK_SIZE_IN_NAME);
        }
        return new Guess(QuantityUnit.PIECE, Reason.NO_MARKER);
    }

    /**
     * Writes the proposal, weighed units first, so the rows most worth checking
     * are at the top.
     */
    public static void write(Path file, List<Row> rows, String approvedFileName)
        throws IOException {
        List<Row> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparing((Row r) -> r.unit() == QuantityUnit.PIECE)
            .thenComparing(Row::unit)
            .thenComparing(Row::reason)
            .thenComparing(r -> r.name() == null ? "" : r.name()));

        StringBuilder out = new StringBuilder(HEADER.formatted(approvedFileName));
        for (Row r : sorted) {
            out.append(r.unit().name()).append('\t')
                .append(r.purchases()).append('\t')
                .append(r.productId().toHexString()).append('\t')
                .append(clean(r.name())).append('\t')
                .append(r.reason().text).append('\n');
        }
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(file, out, StandardCharsets.UTF_8);
    }

    /**
     * Reads a reviewed file back as product id to unit.
     *
     * @throws IllegalStateException naming the line, for anything it cannot
     *                               read with certainty
     */
    public static Map<ObjectId, QuantityUnit> read(Path file) throws IOException {
        Map<ObjectId, QuantityUnit> units = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank() || line.stripLeading().startsWith("#")) {
                continue;
            }
            String where = file + " line " + (i + 1);
            String[] cols = line.split("\t", -1);
            if (cols.length < 3) {
                throw new IllegalStateException(where + ": expected tab-separated "
                    + "unit, purchases, product id; got \"" + line + "\"");
            }
            QuantityUnit unit;
            try {
                unit = QuantityUnit.valueOf(cols[0].strip().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(where + ": unknown unit \"" + cols[0].strip()
                    + "\"; use one of PIECE, KILOGRAM, LITER, GRAM, MILLILITER");
            }
            String id = cols[2].strip();
            if (!ObjectId.isValid(id)) {
                throw new IllegalStateException(where + ": \"" + id + "\" is not a product id");
            }
            if (units.put(new ObjectId(id), unit) != null) {
                throw new IllegalStateException(where + ": product " + id + " is listed twice");
            }
        }
        return units;
    }

    /** Keeps a name on one line and in its column. */
    private static String clean(String name) {
        return name == null ? "(product not found)" : name.replaceAll("[\\t\\r\\n]+", " ");
    }
}
