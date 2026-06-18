package dev.vasoft.homeapp.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;

import dev.vasoft.homeapp.auth.api.controllers.AuthController;
import dev.vasoft.homeapp.auth.services.TokenService;
import dev.vasoft.homeapp.receipts.common.config.WebConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(AuthController.class)
@Import({SecurityConfiguration.class, WebConfig.class})
@ActiveProfiles("dev")
@TestPropertySource(properties = "app.cors.allowed-origins=https://localhost:5173")
class SecurityCorsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TokenService tokenService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void authTokenPreflightAllowsConfiguredFrontendOrigin() throws Exception {
        MvcResult result = mockMvc.perform(options("/api/auth/token")
                .header("Origin", "https://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type"))
            .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getHeader("Access-Control-Allow-Origin"))
            .isEqualTo("https://localhost:5173");
        assertThat(result.getResponse().getHeader("Access-Control-Allow-Credentials"))
            .isEqualTo("true");
    }
}
