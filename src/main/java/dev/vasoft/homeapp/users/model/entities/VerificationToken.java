package dev.vasoft.homeapp.users.model.entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerificationToken {

    @Id
    private ObjectId id;

    @DocumentReference
    @Indexed(unique = true)
    private User user;

    private String token;
    private LocalDateTime expirationDate;

    public VerificationToken(User user, String token) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        if (token == null) {
            throw new IllegalArgumentException("Token cannot be null");
        }
        this.user = user;
        this.token = token;
        this.expirationDate = LocalDateTime.now()
                                           .plusDays(1L);
    }
    
    public boolean isSameAs(String token) {
        return this.token.equals(token);
    }
}
