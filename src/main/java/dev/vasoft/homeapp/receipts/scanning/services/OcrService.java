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

            BufferedImage ocrInput;
            if (isScreenshot(file)) {
                ocrInput = image;
            } else {
                ocrInput = preprocessImage(image);
                saveDebugImage(ocrInput, file.getOriginalFilename());
            }

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
    private boolean isScreenshot(MultipartFile file) {
        String contentType = file.getContentType();
        boolean screenshot = contentType != null && contentType.equalsIgnoreCase("image/png");
        if (screenshot) {
            logger.info("PNG detected — skipping preprocessing (screenshot/digital image)");
        }
        return screenshot;
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