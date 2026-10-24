package dev.vasoft.homeapp.receipts.products.model.entities;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

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

	public Product(String canonicalName) {
		this.canonicalName = canonicalName;
	}

    public Product(ObjectId id) {
        this.id = id;
    }
}