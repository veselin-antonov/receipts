package dev.vasoft.homeapp.auth.api.controllers;

import dev.vasoft.homeapp.auth.services.UserNotActiveException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthControllerAdvice {

    /**
     * The stable {@code error} code for a correct login to an account whose
     * e-mail is not verified yet. Clients key off this, never off the bare 403,
     * which unrelated conditions such as a CORS rejection also produce (D15).
     */
    public static final String ACCOUNT_NOT_VERIFIED = "ACCOUNT_NOT_VERIFIED";

    static ResponseEntity<ProblemDetail> accountNotVerified() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
            "The account's e-mail address has not been verified yet.");
        problem.setTitle("Account Not Verified");
        problem.setProperty("error", ACCOUNT_NOT_VERIFIED);

        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(problem);
    }

    @ExceptionHandler(UserNotActiveException.class)
    public ResponseEntity<ProblemDetail> handleUserNotActiveException(UserNotActiveException e) {
        return accountNotVerified();
    }
}
