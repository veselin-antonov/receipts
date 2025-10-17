package dev.vasoft.homeapp.users.api.request;

public record ReqRegisterUser(
        String email,
        String password
) {
}