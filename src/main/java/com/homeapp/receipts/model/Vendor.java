package com.homeapp.receipts.model;

import lombok.Data;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "vendors")
public class Vendor {
    @Id
    private ObjectId id;
    private String name;
    private String logoURL;

    public Vendor(String name, String logoURL) {
        this.name = name;
        this.logoURL = logoURL;
    }
}
