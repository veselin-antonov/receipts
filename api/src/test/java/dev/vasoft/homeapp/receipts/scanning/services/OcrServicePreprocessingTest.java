package dev.vasoft.homeapp.receipts.scanning.services;

import static org.assertj.core.api.Assertions.assertThat;

import dev.vasoft.homeapp.receipts.scanning.config.OcrProperties;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Covers the two routing decisions that decide how an upload is processed.
 *
 * <p>Both were previously wrong in ways that produced silently bad output rather
 * than errors: thresholding ran over the whole frame including the table, and
 * "is this a screenshot" was decided by the file extension.
 */
class OcrServicePreprocessingTest {

    private OcrService service() {
        OcrProperties props = new OcrProperties("/usr/share/tessdata", "eng", "");
        return new OcrService(Mockito.mock(net.sourceforge.tess4j.Tesseract.class), props);
    }

    private BufferedImage receiptOnSurface(int w, int h, Color surface,
                                           int rx, int ry, int rw, int rh) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(surface);
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(235, 235, 232));          // paper
        g.fillRect(rx, ry, rw, rh);
        g.setColor(new Color(40, 40, 40));             // a few lines of "text"
        for (int y = ry + 20; y < ry + rh - 20; y += 24) {
            g.fillRect(rx + 12, y, rw - 40, 6);
        }
        g.dispose();
        return img;
    }

    private BufferedImage crop(OcrService s, BufferedImage in) {
        return (BufferedImage) ReflectionTestUtils.invokeMethod(s, "cropToReceipt", in);
    }

    @Test
    @DisplayName("crops to the receipt when it sits on a dark surface")
    void cropsToReceipt() {
        BufferedImage img = receiptOnSurface(1200, 1600, new Color(35, 70, 55),
            300, 200, 560, 1100);
        BufferedImage out = crop(service(), img);

        assertThat(out.getWidth()).as("width should collapse toward the receipt")
            .isLessThan(900).isGreaterThan(400);
        assertThat(out.getHeight()).isLessThan(1400).isGreaterThan(900);
        assertThat((long) out.getWidth() * out.getHeight())
            .as("the crop must be a real reduction")
            .isLessThan((long) (0.7 * img.getWidth() * img.getHeight()));
    }

    @Test
    @DisplayName("keeps the whole frame when the receipt already fills it")
    void keepsFrameWhenReceiptFillsIt() {
        BufferedImage img = receiptOnSurface(1000, 1400, new Color(240, 240, 240),
            5, 5, 990, 1390);
        BufferedImage out = crop(service(), img);
        assertThat(out.getWidth()).isEqualTo(img.getWidth());
        assertThat(out.getHeight()).isEqualTo(img.getHeight());
    }

    @Test
    @DisplayName("keeps the whole frame rather than returning a nonsense crop")
    void keepsFrameWhenNothingFound() {
        // Uniform mid-grey: no paper to find. Must degrade to previous behaviour.
        BufferedImage img = new BufferedImage(800, 1000, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(128, 128, 128));
        g.fillRect(0, 0, 800, 1000);
        g.dispose();
        BufferedImage out = crop(service(), img);
        assertThat(out.getWidth()).isEqualTo(800);
        assertThat(out.getHeight()).isEqualTo(1000);
    }

    @Test
    @DisplayName("preprocessing leaves a rendered page essentially unchanged")
    void safeOnRenderedText() {
        // There is no photo-vs-screenshot branch any more, so the one pipeline has
        // to be harmless on clean rendered text. Measured on a real app screenshot
        // it returns the same 13/15 tokens as the raw image; here we assert the
        // structural part - a page that already fills the frame is not cropped.
        BufferedImage page = new BufferedImage(800, 2400, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = page.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 800, 2400);
        g.setColor(Color.BLACK);
        for (int y = 40; y < 2360; y += 30) {
            g.fillRect(40, y, 700, 10);
        }
        g.dispose();
        BufferedImage out = crop(service(), page);
        assertThat(out.getWidth()).isEqualTo(800);
        assertThat(out.getHeight()).isEqualTo(2400);
    }
}
