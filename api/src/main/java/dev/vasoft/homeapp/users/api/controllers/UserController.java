package dev.vasoft.homeapp.users.api.controllers;

import dev.vasoft.homeapp.users.api.request.ReqRegisterUser;
import dev.vasoft.homeapp.users.api.response.ResRegisterUser;
import dev.vasoft.homeapp.users.exceptions.UserAlreadyExistsException;
import dev.vasoft.homeapp.users.services.UserService;
import dev.vasoft.homeapp.users.services.exceptions.InvalidVerificationLinkException;
import dev.vasoft.homeapp.users.services.exceptions.MissingVerificationTokenException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<ResRegisterUser> registerUser(
        @RequestBody ReqRegisterUser reqRegisterUser, HttpServletRequest request) {
        try {
            ResRegisterUser user = userService.registerUser(reqRegisterUser, request);

            return ResponseEntity.status(HttpStatus.CREATED).body(user);
        } catch (UserAlreadyExistsException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<Void> verifyAccount(@RequestParam("user") String userEmail,
        @RequestParam("token") String token) {
        try {
            userService.verifyAccount(userEmail, token);
            return ResponseEntity.ok().build();
        } catch (MissingVerificationTokenException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (InvalidVerificationLinkException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<String> resendVerification(Authentication authentication) {
        userService.resendVerification(authentication);

        return ResponseEntity.ok("Verification email resent");
    }
}