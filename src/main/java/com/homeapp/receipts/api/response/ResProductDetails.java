package com.homeapp.receipts.api.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
public class ResProductDetails {

    private ResStatistics stats;
    private List<ResPurchase> purchases;
}
