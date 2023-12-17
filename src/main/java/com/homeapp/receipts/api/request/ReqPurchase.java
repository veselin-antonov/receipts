package com.homeapp.receipts.api.request;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ReqPurchase {
    private final Double price;
    private final LocalDate date;
    private final String storeID;
    private final Boolean discount;
}
