package dev.vasoft.homeapp.users.model.entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "users")
public class User {
    @Id
    private ObjectId id;
    @Field
    private String email;
    @Field
    private String password;
    @Field
    private boolean isActive;

    public User(String email, String password) {
        this(null, email, password, false);
    }
}
