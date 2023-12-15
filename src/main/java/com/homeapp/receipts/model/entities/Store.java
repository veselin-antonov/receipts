package com.homeapp.receipts.model.entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "stores")
public class Store {
    @Id
    private String id;
    @Field
    private String name;
    @Field
    private String logoURL;

    public Store(String name, String logoURL) {
        this.name = name;
        this.logoURL = logoURL;
    }
}
