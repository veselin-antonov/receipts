package dev.vasoft.homeapp.auth.api.controllers;

import dev.vasoft.homeapp.auth.services.UserNotActiveException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthControllerAdvice {
    @ExceptionHandler(UserNotActiveException.class)
    public ResponseEntity<String> handleUserNotActiveException(UserNotActiveException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("ACCOUNT_NOT_ACTIVATED");
    }
}
