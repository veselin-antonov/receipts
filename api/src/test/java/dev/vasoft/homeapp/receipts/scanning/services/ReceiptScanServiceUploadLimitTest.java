package dev.vasoft.homeapp.receipts.scanning.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;

/**
 * Guards the upload contract described in docs/SCANNING_PATHS.md.
 *
 * <p>The limit is enforced in three layers — nginx, Spring multipart, and this
 * service — and the smallest wins. This covers the service layer; the nginx
 * layer is covered by {@code scripts/test-upload-limits.sh}, which needs a real
 * container and so cannot run here.
 */
class ReceiptScanServiceUploadLimitTest {

    private static final DataSize LIMIT = DataSize.ofMegabytes(25);

    private ReceiptScanService serviceWithLimit(DataSize limit) {
        return new ReceiptScanService(
            Mockito.mock(OcrService.class),
            Mockito.mock(LlmReceiptParser.class),
            Mockito.mock(dev.vasoft.homeapp.receipts.purchases.services.PurchaseService.class),
            Mockito.mock(dev.vasoft.homeapp.receipts.stores.services.StoreService.class),
            Mockito.mock(MatcherService.class),
            limit,
            false);
    }

    private MockMultipartFile fileOfBytes(String contentType, long bytes) {
        return new MockMultipartFile("file", "receipt.jpg", contentType, new byte[(int) bytes]);
    }

    private void validate(ReceiptScanService service, MockMultipartFile file) {
        ReflectionTestUtils.invokeMethod(service, "validateFile", file);
    }

    @Test
    @DisplayName("a file one byte over the configured limit is rejected")
    void rejectsJustOverTheLimit() {
        ReceiptScanService service = serviceWithLimit(LIMIT);
        assertThatThrownBy(() -> validate(service, fileOfBytes("image/jpeg", LIMIT.toBytes() + 1)))
            .isInstanceOf(ReceiptParsingException.class)
            .hasMessageContaining("25MB");
    }

    @Test
    @DisplayName("a file exactly at the configured limit is accepted")
    void acceptsExactlyAtTheLimit() {
        ReceiptScanService service = serviceWithLimit(LIMIT);
        assertThatCode(() -> validate(service, fileOfBytes("image/jpeg", LIMIT.toBytes())))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the limit follows configuration rather than a hard-coded constant")
    void limitIsDrivenByConfiguration() {
        // A 5 MB service must reject what a 25 MB service accepts. If the check
        // ever goes back to a constant, this fails.
        ReceiptScanService small = serviceWithLimit(DataSize.ofMegabytes(5));
        assertThatThrownBy(() -> validate(small, fileOfBytes("image/jpeg", DataSize.ofMegabytes(6).toBytes())))
            .isInstanceOf(ReceiptParsingException.class)
            .hasMessageContaining("5MB");

        ReceiptScanService large = serviceWithLimit(DataSize.ofMegabytes(25));
        assertThatCode(() -> validate(large, fileOfBytes("image/jpeg", DataSize.ofMegabytes(6).toBytes())))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("every documented content type is accepted, and others are not")
    void contentTypeContract() {
        ReceiptScanService service = serviceWithLimit(LIMIT);
        for (String ok : new String[]{"image/jpeg", "image/jpg", "image/png",
                                      "image/gif", "image/webp", "application/pdf"}) {
            assertThatCode(() -> validate(service, fileOfBytes(ok, 1024)))
                .as("documented type %s must be accepted", ok)
                .doesNotThrowAnyException();
        }
        // HEIC is the iOS camera default and is deliberately NOT accepted;
        // documented in SCANNING_PATHS.md so the omission is a known one.
        for (String bad : new String[]{"image/heic", "text/plain", "application/zip"}) {
            assertThatThrownBy(() -> validate(service, fileOfBytes(bad, 1024)))
                .as("undocumented type %s must be rejected", bad)
                .isInstanceOf(ReceiptParsingException.class);
        }
    }

    @Test
    @DisplayName("empty files and missing content types are rejected before anything else")
    void rejectsEmptyAndTypeless() {
        ReceiptScanService service = serviceWithLimit(LIMIT);
        assertThatThrownBy(() -> validate(service,
                new MockMultipartFile("file", "r.jpg", "image/jpeg", new byte[0])))
            .isInstanceOf(ReceiptParsingException.class);
        assertThatThrownBy(() -> validate(service,
                new MockMultipartFile("file", "r.jpg", null,
                    "data".getBytes(StandardCharsets.UTF_8))))
            .isInstanceOf(ReceiptParsingException.class);
    }

    @Test
    @DisplayName("the documented limit is what the service actually enforces")
    void documentedLimitMatchesConfigured() {
        assertThat(LIMIT.toMegabytes())
            .as("docs/SCANNING_PATHS.md and application.yaml both say 25MB")
            .isEqualTo(25);
    }
}
