package com.homeapp.receipts.model.entities;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

import java.time.LocalDate;

@Data
@Document(collection="purchases")
public class Purchase {
    @Id
    private String id;
    @DocumentReference
    private Product product;
    private Double price;
    private LocalDate date;
    @DocumentReference
    private Store store;
    private Boolean discount;

    public Purchase(Product product, Double price, LocalDate date, Store store, Boolean discount) {
        this.product = product;
        this.price = price;
        this.date = date;
        this.store = store;
        this.discount = discount;
    }
}
