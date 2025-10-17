package dev.vasoft.homeapp.users.services.exceptions;

public class MissingVerificationTokenException extends RuntimeException {
    public MissingVerificationTokenException(String message) {
        super(message);
    }

    public MissingVerificationTokenException() {
        super("Verification token not found for user");
    }
}
