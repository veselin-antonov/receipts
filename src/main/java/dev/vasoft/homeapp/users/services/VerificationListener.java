package dev.vasoft.homeapp.users.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class VerificationListener implements ApplicationListener<VerificationRequested> {
    private final VerificationTokenService tokenService;

    @Autowired
    public VerificationListener(VerificationTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public void onApplicationEvent(VerificationRequested event) {
        tokenService.sendVerification(event.getUser());
    }
}
