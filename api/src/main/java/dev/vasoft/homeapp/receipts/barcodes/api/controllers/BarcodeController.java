package dev.vasoft.homeapp.receipts.barcodes.api.controllers;

import dev.vasoft.homeapp.receipts.barcodes.api.response.ResBarcodeProduct;
import dev.vasoft.homeapp.receipts.barcodes.services.BarcodeService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Barcode lookup and link-on-first-scan. Lives beside {@code ProductController}
 * under the same prefix rather than inside it, so product search work and
 * barcode work do not edit the same file.
 */
@RestController
@RequestMapping("/api/products")
public class BarcodeController {

    private final BarcodeService barcodeService;

    public BarcodeController(BarcodeService barcodeService) {
        this.barcodeService = barcodeService;
    }

    @GetMapping("/by-barcode/{barcode}")
    public ResBarcodeProduct findByBarcode(@PathVariable String barcode) {
        return barcodeService.findByBarcode(barcode);
    }

    @PutMapping("/{productId}/barcodes/{barcode}")
    public ResBarcodeProduct link(@PathVariable String productId,
                                  @PathVariable String barcode) {
        return barcodeService.link(productId, barcode);
    }

    @DeleteMapping("/{productId}/barcodes/{barcode}")
    public ResBarcodeProduct unlink(@PathVariable String productId,
                                    @PathVariable String barcode) {
        return barcodeService.unlink(productId, barcode);
    }
}
