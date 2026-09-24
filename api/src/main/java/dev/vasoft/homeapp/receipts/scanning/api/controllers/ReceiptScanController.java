package dev.vasoft.homeapp.receipts.scanning.api.controllers;

import dev.vasoft.homeapp.auth.services.AuthenticatedUserService;
import dev.vasoft.homeapp.receipts.purchases.api.response.ResPurchase;
import dev.vasoft.homeapp.receipts.scanning.api.request.ReqSubmitPurchases;
import dev.vasoft.homeapp.receipts.scanning.api.response.ResScanResult;
import dev.vasoft.homeapp.receipts.scanning.services.ReceiptScanService;
import jakarta.validation.Valid;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Controller for receipt scanning operations.
 * Provides endpoints for uploading receipts and submitting parsed purchases.
 */
@RestController
@RequestMapping("/api/receipts")
public class ReceiptScanController {

    private final Logger logger;
    private final ReceiptScanService receiptScanService;
    private final AuthenticatedUserService authenticatedUserService;

    @Autowired
    public ReceiptScanController(ReceiptScanService receiptScanService,
            AuthenticatedUserService authenticatedUserService) {
        this.logger = LoggerFactory.getLogger(ReceiptScanController.class);
        this.receiptScanService = receiptScanService;
        this.authenticatedUserService = authenticatedUserService;
    }

    /**
     * Scans an uploaded receipt image and returns parsed purchase data.
     * The parsed data can be reviewed and edited by the user before submission.
     *
     * @param jwt  The authenticated user's JWT token
     * @param file The receipt image file (JPEG, PNG, GIF, WebP, or PDF)
     * @return Parsed receipt data with store info and line items
     */
    @PostMapping(value = "/scan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResScanResult scanReceipt(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("file") MultipartFile file) {
        logger.info("User {} scanning receipt: {}", jwt.getSubject(), file.getOriginalFilename());

        return receiptScanService.scanReceipt(file);
    }

    /**
     * Submits parsed (and potentially user-edited) purchases for persistence.
     * This is called after the user reviews and confirms the scanned data.
     *
     * @param jwt      The authenticated user's JWT token
     * @param request  The list of purchases to save
     * @return List of created purchase records
     */
    @PostMapping("/submit")
    public List<ResPurchase> submitPurchases(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ReqSubmitPurchases request) {
        ObjectId userId = authenticatedUserService.getUserId(jwt);
        logger.info("User {} submitting {} purchases", userId, request.purchases().size());

        return receiptScanService.submitPurchases(userId, request.purchases());
    }
}