package dev.vasoft.homeapp.users.services.exceptions;

public class InvalidVerificationLinkException extends RuntimeException {
    public InvalidVerificationLinkException() {
        super("The verification link is invalid or has expired.");
    }
}
