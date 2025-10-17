package dev.vasoft.homeapp.users.services.mappers;

import dev.vasoft.homeapp.users.api.response.ResRegisterUser;
import dev.vasoft.homeapp.users.model.entities.User;

public class UserMapper {

    private UserMapper() {
    }

    public static ResRegisterUser toRegisterUserResponse(User user) {
        return new ResRegisterUser(user.getEmail());
    }
}