package dev.vasoft.homeapp.auth.services;

public class UserNotActiveException extends RuntimeException {
    public UserNotActiveException() {
        super("User account is not active");
    }

    public UserNotActiveException(String message) {
        super(message);
    }
}