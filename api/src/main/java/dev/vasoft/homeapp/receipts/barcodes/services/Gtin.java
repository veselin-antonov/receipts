package dev.vasoft.homeapp.receipts.barcodes.services;

/**
 * Validates a scanned retail barcode and reduces it to one canonical form, so
 * the same pack finds the same product whichever way a scanner reports it.
 *
 * <p>Accepted: EAN-8, UPC-A, EAN-13 and GTIN-14, digits only, with a correct
 * GS1 check digit. A misread digit almost always breaks the check digit, so a
 * bad one is rejected rather than linked.
 *
 * <p>Canonical form: UPC-A is EAN-13 with a leading zero, and a GTIN-14 with
 * indicator digit 0 is the EAN-13 behind it, so both are stored as EAN-13. The
 * check digit does not change when zeros are added on the left. EAN-8 stays as
 * it is; zero-padding it would make it a different number.
 */
public final class Gtin {

    private Gtin() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            throw new InvalidBarcodeException(null, "no barcode given");
        }
        String code = raw.strip();
        if (code.isEmpty() || !code.chars().allMatch(c -> c >= '0' && c <= '9')) {
            throw new InvalidBarcodeException(raw, "a barcode is digits only");
        }
        if (code.length() != 8 && code.length() != 12 && code.length() != 13
            && code.length() != 14) {
            throw new InvalidBarcodeException(raw,
                "expected 8, 12, 13 or 14 digits, got " + code.length());
        }
        if (!hasValidCheckDigit(code)) {
            throw new InvalidBarcodeException(raw, "the check digit does not match");
        }
        if (code.length() == 12) {
            return "0" + code;
        }
        if (code.length() == 14 && code.charAt(0) == '0') {
            return code.substring(1);
        }
        return code;
    }

    /**
     * GS1 mod-10: from the right, excluding the check digit, weights alternate
     * 3, 1, 3, ...
     */
    static boolean hasValidCheckDigit(String code) {
        int sum = 0;
        int last = code.length() - 1;
        for (int i = last - 1, position = 0; i >= 0; i--, position++) {
            int digit = code.charAt(i) - '0';
            sum += position % 2 == 0 ? digit * 3 : digit;
        }
        int expected = (10 - sum % 10) % 10;
        return code.charAt(last) - '0' == expected;
    }
}
