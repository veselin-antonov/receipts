package dev.vasoft.homeapp.auth.config.filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtCookieFilter extends OncePerRequestFilter {

    public static final String JWT_COOKIE = "JWT";

    private final JwtDecoder jwtDecoder;

    public JwtCookieFilter(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    // Create a wrapped request with the jwt token added to the Authorization header
    private static HttpServletRequestWrapper getHttpServletRequestWrapper(
        HttpServletRequest request, String token) {
        return new HttpServletRequestWrapper(request) {
            @Override
            public String getHeader(String name) {
                if ("Authorization".equalsIgnoreCase(name)) {
                    return "Bearer " + token;
                }
                return super.getHeader(name);
            }
        };
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain) throws ServletException, IOException {

        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            filterChain.doFilter(request, response);
            return;
        }

        for (Cookie cookie : cookies) {
            if (JWT_COOKIE.equals(cookie.getName())) {
                String token = cookie.getValue();

                if (token != null && !token.isEmpty()) {
                    try {
                        jwtDecoder.decode(token);
                        HttpServletRequestWrapper wrapper = getHttpServletRequestWrapper(
                            request, token);
                        filterChain.doFilter(wrapper, response);
                        return;
                    } catch (JwtException e) {
                        Cookie invalidCookie = new Cookie(JWT_COOKIE, null);
                        invalidCookie.setMaxAge(0);
                        invalidCookie.setHttpOnly(true);
                        invalidCookie.setPath("/");
                        response.addCookie(invalidCookie);
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
