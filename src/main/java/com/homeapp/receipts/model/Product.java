package com.homeapp.receipts.model;

import lombok.Data;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

@Data
@Document(collection="products")
public class Product {
    @Id
    private ObjectId id;
    private String name;
    private Double price;
    @DocumentReference
    private Vendor vendor;

    public Product(String name, Double price, Vendor vendor) {
        this.name = name;
        this.price = price;
        this.vendor = vendor;
    }
}
