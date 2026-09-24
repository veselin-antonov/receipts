package dev.vasoft.homeapp.users.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class RegistrationListener implements ApplicationListener<UserRegistrationCompleted> {
    private final VerificationTokenService tokenService;

    @Autowired
    public RegistrationListener(VerificationTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public void onApplicationEvent(UserRegistrationCompleted event) {
        tokenService.sendVerification(event.getUser());
    }
}
