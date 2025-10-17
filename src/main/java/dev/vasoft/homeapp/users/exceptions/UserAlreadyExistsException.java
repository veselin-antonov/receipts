package dev.vasoft.homeapp.users.exceptions;

public class UserAlreadyExistsException extends RuntimeException {

    public UserAlreadyExistsException() {
        super("User with the given email already exists.");
    }
}