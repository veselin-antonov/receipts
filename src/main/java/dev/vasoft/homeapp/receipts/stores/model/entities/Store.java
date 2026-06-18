package dev.vasoft.homeapp.receipts.stores.model.entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "stores")
public class Store {

    @Id
    private ObjectId id;
    private String canonicalName;
    @Indexed
    private String normalizedCanonicalName;
    private String iconID;

    public Store(String canonicalName) {
        this.canonicalName = canonicalName;
    }

    public Store(ObjectId id) {
        this.id = id;
    }
}