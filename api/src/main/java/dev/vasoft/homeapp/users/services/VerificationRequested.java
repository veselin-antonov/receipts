package dev.vasoft.homeapp.users.services;

import dev.vasoft.homeapp.users.model.entities.User;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class VerificationRequested extends ApplicationEvent {
    private final User user;

    public VerificationRequested(User user, Object source) {
        super(source);
        this.user = user;
    }
}
