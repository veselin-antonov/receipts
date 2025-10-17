package dev.vasoft.homeapp.auth.services;

import dev.vasoft.homeapp.users.model.entities.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

@Getter
public class CustomUserDetails implements UserDetails {

    private final String username;
    private final String password;
    private final boolean active;
    private final List<? extends GrantedAuthority> authorities;

    public CustomUserDetails(String username, String password, boolean active) {
        this.username = username;
        this.password = password;
        this.active = active;
        this.authorities = List.of();
    }

    public CustomUserDetails(String username, String password, boolean active,
                             List<? extends GrantedAuthority> authorities) {
        this.username = username;
        this.password = password;
        this.active = active;
        this.authorities = authorities;
    }

    public static CustomUserDetails fromUserEntity(User user) {
        return new CustomUserDetails(user.getEmail(), user.getPassword(), user.isActive());
    }
}
