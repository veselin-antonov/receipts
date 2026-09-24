package dev.vasoft.homeapp.receipts.scanning.services;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.vasoft.homeapp.receipts.purchases.services.PurchaseService;
import dev.vasoft.homeapp.receipts.stores.services.StoreService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

/** A scan that yields nothing is a 422 (ReceiptParsingException), never a 500 or an empty 200. */
class ReceiptScanServiceEmptyScanTest {

    private final LlmReceiptParser parser = mock(LlmReceiptParser.class);

    private final ReceiptScanService service = new ReceiptScanService(mock(OcrService.class), parser,
        mock(PurchaseService.class), mock(StoreService.class), mock(MatcherService.class),
        DataSize.ofMegabytes(25), false);

    // PDFs go straight to the parser, so no OCR mock is needed.
    private final MockMultipartFile pdf =
        new MockMultipartFile("file", "receipt.pdf", "application/pdf", new byte[] {1, 2, 3});

    @Test
    void aNullParserResultIsAParsingFailureNotANullPointer() {
        when(parser.parseReceipt(pdf)).thenReturn(null);

        assertThatThrownBy(() -> service.scanReceipt(pdf))
            .isInstanceOf(ReceiptParsingException.class)
            .hasMessageContaining("No purchases could be read");
    }

    @Test
    void anEmptyItemListIsAParsingFailure() {
        when(parser.parseReceipt(pdf)).thenReturn(new ParsedReceipt("Billa", null, List.of()));

        assertThatThrownBy(() -> service.scanReceipt(pdf))
            .isInstanceOf(ReceiptParsingException.class);
    }
}
