package dev.vasoft.homeapp.users.services;

import dev.vasoft.homeapp.users.model.entities.User;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class UserRegistrationCompleted extends ApplicationEvent {

    private final User user;

    public UserRegistrationCompleted(User user, Object source) {
        super(source);
        this.user = user;
    }
}