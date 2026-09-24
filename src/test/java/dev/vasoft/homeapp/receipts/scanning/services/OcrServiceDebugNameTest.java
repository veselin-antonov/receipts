package dev.vasoft.homeapp.receipts.scanning.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The debug image name comes from the client's upload, so it must not steer the path. */
class OcrServiceDebugNameTest {

    @Test
    void keepsAnOrdinaryNameWithoutItsExtension() {
        assertThat(OcrService.debugBaseName("billa_01_flat.jpeg")).isEqualTo("billa_01_flat");
    }

    @Test
    void dropsUnixTraversalAndAbsolutePaths() {
        assertThat(OcrService.debugBaseName("../../etc/cron.d/evil.png")).isEqualTo("evil");
        assertThat(OcrService.debugBaseName("/tmp/receipt.jpg")).isEqualTo("receipt");
    }

    @Test
    void dropsWindowsPaths() {
        assertThat(OcrService.debugBaseName("C:\\Users\\x\\..\\receipt 1.jpg")).isEqualTo("receipt_1");
    }

    @Test
    void neverReturnsSomethingThatClimbsOrHides() {
        assertThat(OcrService.debugBaseName("..")).isEqualTo("unknown");
        assertThat(OcrService.debugBaseName("...hidden.png")).isEqualTo("hidden");
        assertThat(OcrService.debugBaseName(null)).isEqualTo("unknown");
        assertThat(OcrService.debugBaseName("")).isEqualTo("unknown");
    }

    @Test
    void theResolvedFileAlwaysStaysInsideTheDebugDirectory() {
        Path dir = Path.of("/var/ocr-debug");
        for (String hostile : new String[] {"../../x.png", "/etc/passwd", "a/../../b.jpg", "..\\..\\c.jpg"}) {
            Path resolved = dir.resolve(OcrService.debugBaseName(hostile) + "_t_preprocessed.png").normalize();
            assertThat(resolved.getParent()).isEqualTo(dir);
        }
    }

    @Test
    void replacesCharactersOutsideTheSafeSet() {
        assertThat(OcrService.debugBaseName("касова бележка.jpg")).matches("[A-Za-z0-9._-]+");
    }
}
