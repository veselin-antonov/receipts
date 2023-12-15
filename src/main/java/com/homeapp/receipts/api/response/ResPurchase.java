package com.homeapp.receipts.api.response;

import lombok.Data;

@Data
public class ResPurchase {
    private final String price;
    private final String date;
    private final ResStore store;
    private final Boolean discount;
}
