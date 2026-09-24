package dev.vasoft.homeapp.receipts.scanning.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    @Test
    void theBoundLeavesRoomForTheTimestampAndSuffix() {
        assertThat(OcrService.MAX_DEBUG_BASE_NAME).isEqualTo(222);
    }

    @Test
    void anOverlongNameIsCutSoTheSavedFileFitsIn255Bytes() {
        String base = OcrService.debugBaseName("a".repeat(300) + ".jpg");
        String saved = base + "_"
            + LocalDateTime.now().format(DateTimeFormatter.ofPattern(OcrService.DEBUG_TIMESTAMP_PATTERN))
            + OcrService.DEBUG_SUFFIX;

        assertThat(base).hasSize(OcrService.MAX_DEBUG_BASE_NAME);
        assertThat(saved.getBytes(StandardCharsets.UTF_8)).hasSize(255);
    }

    @Test
    void aNameAtTheBoundIsKeptWhole() {
        String name = "b".repeat(OcrService.MAX_DEBUG_BASE_NAME);
        assertThat(OcrService.debugBaseName(name + ".png")).isEqualTo(name);
    }
}
