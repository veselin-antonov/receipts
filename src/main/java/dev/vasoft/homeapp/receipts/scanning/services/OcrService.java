package dev.vasoft.homeapp.receipts.scanning.services;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import dev.vasoft.homeapp.receipts.scanning.config.OcrProperties;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Service for extracting text from receipt images using Tesseract OCR.
 * Handles EXIF orientation correction and optional preprocessing
 * (upscaling, sharpening, adaptive binarization) for camera photos.
 */
@Service
public class OcrService {

    private static final int TARGET_MIN_HEIGHT = 2000;
    /** Nothing below this can be paper, whatever the histogram says. */
    private static final int MIN_PAPER_LUMINANCE = 140;
    /** How far below the paper level still counts as paper, for shadowed edges. */
    private static final int PAPER_TOLERANCE = 45;
    /** Fraction of a row that must be bright before it counts as part of the receipt. */
    private static final double MIN_ROW_COVERAGE = 0.06;
    /** Above this, cropping gains nothing; below it, the detection is not credible. */
    private static final double MAX_USEFUL_CROP = 0.95;
    private static final double MIN_PLAUSIBLE_CROP = 0.04;
    private static final int OTSU_HISTOGRAM_BINS = 256;
    private static final DateTimeFormatter DEBUG_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final Logger logger;
    private final Tesseract tesseract;
    private final Path debugOutputPath;

    @Autowired
    public OcrService(Tesseract tesseract, OcrProperties ocrProperties) {
        this.logger = LoggerFactory.getLogger(OcrService.class);
        this.tesseract = tesseract;

        if (ocrProperties.debugOutputPath() != null && !ocrProperties.debugOutputPath().isBlank()) {
            this.debugOutputPath = Path.of(ocrProperties.debugOutputPath());
            logger.info("OCR debug image saving enabled — output directory: {}", this.debugOutputPath);
        } else {
            this.debugOutputPath = null;
        }
    }

    /**
     * Extracts text from a receipt image using Tesseract OCR.
     * Screenshots (PNG) are fed directly to Tesseract since they already have
     * clean, sharp text. Photos (JPEG, etc.) go through EXIF orientation
     * correction and preprocessing first.
     *
     * @param file The uploaded receipt image file
     * @return Extracted text content from the receipt
     * @throws ReceiptParsingException if OCR processing fails
     */
    public String extractText(MultipartFile file) {
        logger.info("Starting OCR text extraction for file: {}", file.getOriginalFilename());

        try {
            byte[] fileBytes = file.getBytes();

            int exifOrientation = readExifOrientation(new ByteArrayInputStream(fileBytes));

            BufferedImage image = ImageIO.read(new ByteArrayInputStream(fileBytes));
            if (image == null) {
                throw new ReceiptParsingException("Could not read image from file: " + file.getOriginalFilename());
            }

            image = applyExifOrientation(image, exifOrientation);

            // One path for every image. There is deliberately no photo-vs-screenshot
            // branch: the previous one keyed off the PNG extension, so a photo
            // exported as PNG skipped the preprocessing it needed while a screenshot
            // saved as JPEG got preprocessing that could destroy it (D19).
            //
            // Replacing that test was tried and abandoned. Colour count does not
            // separate the two on this data - measured across the fixture set,
            // screen captures land at 345-472 distinct colours and photographs at
            // 194-392, overlapping completely, because a receipt photo is itself a
            // low-colour scene. Camera EXIF works but is stripped by messaging apps.
            //
            // The branch turned out to be unnecessary. Measured on a real app
            // screenshot, this pipeline returns 13/15 tokens - identical to feeding
            // it the raw image - because cropping is a no-op when the document
            // already fills the frame, and thresholding clean rendered text is close
            // to identity. Removing the classification removes the bug class.
            BufferedImage cropped = cropToReceipt(image);
            BufferedImage ocrInput = preprocessImage(cropped);
            saveDebugImage(ocrInput, file.getOriginalFilename());

            String text = tesseract.doOCR(ocrInput);

            logger.info("OCR extracted {} characters from file: {}", text.length(), file.getOriginalFilename());
            logger.debug("OCR output:\n{}", text);

            return text;

        } catch (IOException e) {
            throw new ReceiptParsingException("Failed to read image file for OCR", e);
        } catch (TesseractException e) {
            throw new ReceiptParsingException("OCR text extraction failed", e);
        }
    }

    /**
     * Reads the EXIF orientation tag from the image stream.
     * Returns 1 (normal) if no EXIF data is found or on any error.
     */
    private int readExifOrientation(InputStream inputStream) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(inputStream);
            ExifIFD0Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (directory != null && directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                int orientation = directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
                logger.debug("EXIF orientation tag: {}", orientation);
                return orientation;
            }
        } catch (Exception e) {
            logger.debug("No EXIF orientation data found: {}", e.getMessage());
        }
        return 1;
    }


    /**
     * Applies rotation/flip based on EXIF orientation tag.
     * <pre>
     * 1 = Normal                        5 = Mirrored + 270° CW
     * 2 = Mirrored horizontal           6 = 270° CW (most common for portrait photos)
     * 3 = Rotated 180°                  7 = Mirrored + 90° CW
     * 4 = Mirrored vertical             8 = 90° CW
     * </pre>
     */
    private BufferedImage applyExifOrientation(BufferedImage image, int orientation) {
        if (orientation == 1) {
            return image;
        }

        int width = image.getWidth();
        int height = image.getHeight();
        AffineTransform transform = new AffineTransform();

        switch (orientation) {
            case 2 -> { // Mirrored horizontal
                transform.scale(-1, 1);
                transform.translate(-width, 0);
            }
            case 3 -> { // Rotated 180°
                transform.translate(width, height);
                transform.rotate(Math.PI);
            }
            case 4 -> { // Mirrored vertical
                transform.scale(1, -1);
                transform.translate(0, -height);
            }
            case 5 -> { // Mirrored + 270° CW
                transform.rotate(Math.toRadians(270));
                transform.scale(-1, 1);
            }
            case 6 -> { // 270° CW (portrait photo taken with phone held upright)
                transform.translate(height, 0);
                transform.rotate(Math.toRadians(90));
            }
            case 7 -> { // Mirrored + 90° CW
                transform.scale(-1, 1);
                transform.translate(-height, 0);
                transform.translate(0, width);
                transform.rotate(Math.toRadians(270));
            }
            case 8 -> { // 90° CW
                transform.translate(0, width);
                transform.rotate(Math.toRadians(270));
            }
            default -> {
                logger.warn("Unknown EXIF orientation: {}", orientation);
                return image;
            }
        }

        boolean swapDimensions = orientation >= 5;
        int newWidth = swapDimensions ? height : width;
        int newHeight = swapDimensions ? width : height;

        BufferedImage rotated = new BufferedImage(newWidth, newHeight, image.getType());
        Graphics2D g = rotated.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, transform, null);
        g.dispose();

        logger.info("Applied EXIF orientation {} — image rotated from {}x{} to {}x{}",
                orientation, width, height, newWidth, newHeight);

        return rotated;
    }

    /**
     * Heuristic: PNGs are almost always screenshots or digital exports,
     * while JPEGs/WebP are camera photos. Screenshots have clean text that
     * doesn't benefit from preprocessing — in fact, sharpening and binarization
     * can degrade their already pixel-perfect edges.
     */
    /**
     * Crops to the receipt so that thresholding sees paper and ink rather than
     * paper and furniture.
     *
     * <p>Finds the largest bright region — the paper against whatever it is lying
     * on — by scanning rows and columns for a run of pixels above a high
     * percentile. Deliberately simple: no edge detection, no perspective
     * correction. The receipt is the brightest thing in a receipt photo, which
     * is enough.
     *
     * <p>Returns the original image when it cannot find a plausible receipt, so
     * an unusual photo degrades to the previous behaviour rather than failing.
     */
    private BufferedImage cropToReceipt(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int step = Math.max(1, Math.min(w, h) / 400);   // sample, do not read 12M pixels

        int[] histogram = new int[OTSU_HISTOGRAM_BINS];
        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                histogram[luminance(image.getRGB(x, y))]++;
            }
        }
        int sampled = 0;
        for (int count : histogram) {
            sampled += count;
        }
        // Paper sits in the top slice of the histogram; the surface below it.
        int paperLevel = percentile(histogram, sampled, 0.92);
        int threshold = Math.max(MIN_PAPER_LUMINANCE, paperLevel - PAPER_TOLERANCE);

        int top = -1, bottom = -1, left = -1, right = -1;
        for (int y = 0; y < h; y += step) {
            int bright = 0;
            for (int x = 0; x < w; x += step) {
                if (luminance(image.getRGB(x, y)) > threshold) {
                    bright++;
                }
            }
            if (bright > (w / step) * MIN_ROW_COVERAGE) {
                if (top < 0) {
                    top = y;
                }
                bottom = y;
            }
        }
        for (int x = 0; x < w; x += step) {
            int bright = 0;
            for (int y = 0; y < h; y += step) {
                if (luminance(image.getRGB(x, y)) > threshold) {
                    bright++;
                }
            }
            if (bright > (h / step) * MIN_ROW_COVERAGE) {
                if (left < 0) {
                    left = x;
                }
                right = x;
            }
        }

        if (top < 0 || left < 0 || right <= left || bottom <= top) {
            logger.debug("No receipt region found; using the whole frame");
            return image;
        }

        int margin = Math.max(8, Math.min(w, h) / 100);
        int x0 = Math.max(0, left - margin);
        int y0 = Math.max(0, top - margin);
        int x1 = Math.min(w, right + margin);
        int y1 = Math.min(h, bottom + margin);
        double area = ((double) (x1 - x0) * (y1 - y0)) / ((double) w * h);

        // A crop covering almost everything gains nothing; one covering almost
        // nothing means the detection was wrong. Either way, keep the original.
        if (area > MAX_USEFUL_CROP || area < MIN_PLAUSIBLE_CROP) {
            logger.debug("Receipt region covers {}% of the frame; using the whole frame",
                Math.round(area * 100));
            return image;
        }

        logger.info("Cropped to receipt: {}x{} -> {}x{} ({}% of the frame)",
            w, h, x1 - x0, y1 - y0, Math.round(area * 100));
        return image.getSubimage(x0, y0, x1 - x0, y1 - y0);
    }



    private static int luminance(int rgb) {
        return (int) (0.299 * ((rgb >> 16) & 0xFF)
            + 0.587 * ((rgb >> 8) & 0xFF)
            + 0.114 * (rgb & 0xFF));
    }

    private static int percentile(int[] histogram, int total, double fraction) {
        int target = (int) (total * (1 - fraction));
        int seen = 0;
        for (int level = histogram.length - 1; level >= 0; level--) {
            seen += histogram[level];
            if (seen >= target) {
                return level;
            }
        }
        return 255;
    }

    /**
     * Saves the preprocessed image to the debug output directory for visual inspection.
     * Only active when {@code app.ocr.debug-output-path} is configured.
     */
    private void saveDebugImage(BufferedImage image, String originalFilename) {
        if (debugOutputPath == null) {
            return;
        }

        try {
            Files.createDirectories(debugOutputPath);

            String baseName = originalFilename != null
                    ? originalFilename.replaceAll("\\.[^.]+$", "")
                    : "unknown";
            String timestamp = LocalDateTime.now().format(DEBUG_TIMESTAMP);
            String debugFilename = baseName + "_" + timestamp + "_preprocessed.png";
            Path outputFile = debugOutputPath.resolve(debugFilename);

            ImageIO.write(image, "png", outputFile.toFile());
            logger.info("Debug image saved: {}", outputFile);
        } catch (IOException e) {
            logger.warn("Failed to save debug image: {}", e.getMessage());
        }
    }

    /**
     * Preprocesses a receipt image to improve OCR accuracy.
     * Pipeline: upscale → grayscale → sharpen → Otsu binarization.
     */
    private BufferedImage preprocessImage(BufferedImage original) {
        BufferedImage image = upscaleIfNeeded(original);
        BufferedImage grayscale = toGrayscale(image);
        BufferedImage sharpened = sharpen(grayscale);
        return otsuBinarize(sharpened);
    }

    /** Upscales small images so Tesseract has enough pixel detail for digit recognition. */
    private BufferedImage upscaleIfNeeded(BufferedImage image) {
        if (image.getHeight() >= TARGET_MIN_HEIGHT) {
            return image;
        }

        double scale = (double) TARGET_MIN_HEIGHT / image.getHeight();
        int newWidth = (int) (image.getWidth() * scale);
        int newHeight = TARGET_MIN_HEIGHT;

        logger.debug("Upscaling image from {}x{} to {}x{} ({}x)",
                image.getWidth(), image.getHeight(), newWidth, newHeight, String.format("%.1f", scale));

        BufferedImage scaled = new BufferedImage(newWidth, newHeight, image.getType());
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(image, 0, 0, newWidth, newHeight, null);
        g.dispose();
        return scaled;
    }

    private BufferedImage toGrayscale(BufferedImage image) {
        BufferedImage grayscale = new BufferedImage(
                image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null)
                .filter(image, grayscale);
        return grayscale;
    }

    /** Applies an unsharp-mask-style sharpening kernel to enhance digit edges. */
    private BufferedImage sharpen(BufferedImage image) {
        float[] sharpenKernel = {
                 0, -1,  0,
                -1,  5, -1,
                 0, -1,  0
        };
        ConvolveOp op = new ConvolveOp(
                new Kernel(3, 3, sharpenKernel), ConvolveOp.EDGE_NO_OP, null);
        return op.filter(image, null);
    }

    /**
     * Binarizes using Otsu's method — automatically picks the optimal threshold
     * by minimizing intra-class variance. Much better than a fixed threshold
     * for preserving the curves that distinguish digits like 0, 6 and 8.
     */
    private BufferedImage otsuBinarize(BufferedImage grayscale) {
        int width = grayscale.getWidth();
        int height = grayscale.getHeight();

        int[] histogram = new int[OTSU_HISTOGRAM_BINS];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                histogram[grayscale.getRGB(x, y) & 0xFF]++;
            }
        }

        int threshold = computeOtsuThreshold(histogram, width * height);
        logger.debug("Otsu threshold: {}", threshold);

        BufferedImage binary = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = grayscale.getRGB(x, y) & 0xFF;
                binary.setRGB(x, y, pixel > threshold ? 0xFFFFFF : 0x000000);
            }
        }
        return binary;
    }

    /** Computes the optimal binarization threshold via Otsu's method. */
    private int computeOtsuThreshold(int[] histogram, int totalPixels) {
        double sumAll = 0;
        for (int i = 0; i < OTSU_HISTOGRAM_BINS; i++) {
            sumAll += (long) i * histogram[i];
        }

        double sumBackground = 0;
        int weightBackground = 0;
        double maxVariance = 0;
        int bestThreshold = 0;

        for (int t = 0; t < OTSU_HISTOGRAM_BINS; t++) {
            weightBackground += histogram[t];
            if (weightBackground == 0) continue;

            int weightForeground = totalPixels - weightBackground;
            if (weightForeground == 0) break;

            sumBackground += (long) t * histogram[t];

            double meanBackground = sumBackground / weightBackground;
            double meanForeground = (sumAll - sumBackground) / weightForeground;
            double meanDiff = meanBackground - meanForeground;

            double betweenVariance = (double) weightBackground * weightForeground * meanDiff * meanDiff;

            if (betweenVariance > maxVariance) {
                maxVariance = betweenVariance;
                bestThreshold = t;
            }
        }

        return bestThreshold;
    }
}