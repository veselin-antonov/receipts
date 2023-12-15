package com.homeapp.receipts.model.entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection="products")
public class Product {
    @Id
    private String id;
    private String name;
    private String iconID;
    @DocumentReference
    private Statistics statistics;
    @DocumentReference
    private List<Purchase> purchases;

    public Product(String name, String iconUrl) {
        this.name = name;
        this.iconID = iconUrl;
    }
}
