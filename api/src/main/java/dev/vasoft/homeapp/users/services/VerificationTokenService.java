package dev.vasoft.homeapp.users.services;

import dev.vasoft.homeapp.users.model.entities.User;
import dev.vasoft.homeapp.users.model.entities.VerificationToken;
import dev.vasoft.homeapp.users.model.repositories.VerificationTokenRepository;
import java.net.URI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.UUID;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class VerificationTokenService {
    private final JavaMailSender mailSender;
    private final VerificationTokenRepository tokenRepository;

    @Value("${app.host-url}")
    private String appHostUrl;

    @Autowired
    public VerificationTokenService(JavaMailSender mailSender,
                                    VerificationTokenRepository tokenRepository) {
        this.mailSender = mailSender;
        this.tokenRepository = tokenRepository;
    }

    public void sendVerification(User user) {

        VerificationToken verificationToken = new VerificationToken(user,
                                                                    UUID.randomUUID()
                                                                        .toString());

        tokenRepository.save(verificationToken);

        String verificationURL = UriComponentsBuilder.fromUri(URI.create(appHostUrl + "/verify"))
            .queryParam("user", user.getEmail())
            .queryParam("token", verificationToken.getToken())
            .toUriString();

        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setFrom("The HomeApp <thehomeapp@vasoft.dev>");
        mailMessage.setTo(user.getEmail());
        mailMessage.setText(
                "Please verify your email via this link: " + verificationURL);
        mailMessage.setSubject("Verify your profile");

        mailSender.send(mailMessage);
    }
}
