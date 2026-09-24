package dev.vasoft.homeapp.receipts.products.model.entities;

import dev.vasoft.homeapp.receipts.common.model.entities.Statistics;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "products")
public class Product {
	@Id
	private ObjectId id;
	private String canonicalName;
  private String normalizedCanonicalName;
  private List<String> aliases;
  private List<String> normalizedAliases;
	private String iconID;
	@DocumentReference
	private Statistics statistics;

	public Product(String canonicalName) {
		this.canonicalName = canonicalName;
	}

    public Product(ObjectId id) {
        this.id = id;
    }
}