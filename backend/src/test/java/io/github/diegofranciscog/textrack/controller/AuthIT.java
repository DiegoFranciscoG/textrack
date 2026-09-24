package io.github.diegofranciscog.textrack.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.diegofranciscog.textrack.domain.Role;
import io.github.diegofranciscog.textrack.security.RequestSizeLimitFilter;
import io.github.diegofranciscog.textrack.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

@DisplayName("Autenticación, autorización y cabeceras de seguridad")
class AuthIT extends IntegrationTest {

    private String email;
    private String password;

    @BeforeEach
    void createUser() {
        email = "supervisora" + System.nanoTime() + "@it.textrack.test";
        password = randomSecret();
        users.insert(email, "Supervisora de prueba", passwordEncoder.encode(password), Role.SUPERVISOR);
    }

    private MvcResult login(String user, String pass, String ip) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .with(request -> {
                            request.setRemoteAddr(ip);
                            return request;
                        })
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(user, pass)))
                .andReturn();
    }

    @Test
    void loginReturnsShortLivedJwtAndRefreshToken() throws Exception {
        MvcResult result = login(email, password, "10.0.0.1");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String body = result.getResponse().getContentAsString();
        assertThat((Integer) JsonPath.read(body, "$.expiresIn")).isEqualTo(900);
        assertThat((String) JsonPath.read(body, "$.user.role")).isEqualTo("SUPERVISOR");
        String token = JsonPath.read(body, "$.accessToken");

        mvc.perform(get("/api/v1/payroll/daily").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void wrongPasswordAndUnknownUserGiveTheSameGenericError() throws Exception {
        MvcResult wrong = login(email, "incorrecta", "10.0.0.2");
        MvcResult unknown = login("nadie@it.textrack.test", "incorrecta", "10.0.0.2");

        assertThat(wrong.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknown.getResponse().getStatus()).isEqualTo(401);
        assertThat(wrong.getResponse().getContentAsString()).contains("Credenciales inválidas");
        assertThat(unknown.getResponse().getContentAsString()).contains("Credenciales inválidas");
    }

    @Test
    void loginIsRateLimitedPerIpAndUser() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(login(email, "incorrecta", "10.0.0.3").getResponse().getStatus()).isEqualTo(401);
        }
        MvcResult blocked = login(email, password, "10.0.0.3");

        assertThat(blocked.getResponse().getStatus()).isEqualTo(429);
        assertThat(blocked.getResponse().getHeader("Retry-After")).isNotBlank();
    }

    @Test
    void refreshTokenRotatesAndReuseRevokesAllSessions() throws Exception {
        String first = JsonPath.read(login(email, password, "10.0.0.4").getResponse().getContentAsString(),
                "$.refreshToken");

        String rotated = JsonPath.read(mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + first + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.refreshToken");
        assertThat(rotated).isNotEqualTo(first);

        // Un atacante reutiliza el token viejo: se rechaza y se revoca también el nuevo.
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + first + "\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + rotated + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        String refresh = JsonPath.read(login(email, password, "10.0.0.5").getResponse().getContentAsString(),
                "$.refreshToken");

        mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointsRequireAValidToken() throws Exception {
        mvc.perform(get("/api/v1/production-orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/production-orders").header("Authorization", "Bearer no.es.un.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rolesAreEnforced() throws Exception {
        mvc.perform(post("/api/v1/styles").header("Authorization", bearer(Role.VIEWER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ST-NO\",\"name\":\"x\",\"garmentType\":\"x\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/production-orders").header("Authorization", bearer(Role.SCANNER)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/operators").header("Authorization", bearer(Role.SCANNER)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/quality/aql-inspections").header("Authorization", bearer(Role.PLANNER))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidBodyReturnsProblemDetailWithoutInternals() throws Exception {
        mvc.perform(post("/api/v1/styles").header("Authorization", bearer(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"minúsculas\",\"name\":\"\",\"garmentType\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Datos inválidos"))
                .andExpect(jsonPath("$.errors.code").exists())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void securityHeadersArePresent() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")));
    }

    @Test
    void onlyHealthIsExposedByActuator() throws Exception {
        mvc.perform(get("/actuator/env").header("Authorization", bearer(Role.ADMIN)))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(403, 404));
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    }

    @Test
    void oversizedBodiesAreRejectedBeforeParsing() throws Exception {
        byte[] body = new byte[(int) RequestSizeLimitFilter.MAX_BODY_BYTES + 1];
        mvc.perform(post("/api/v1/readings/batch").header("Authorization", bearer(Role.SCANNER))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(413));
    }

    @Test
    void corsAllowsOnlyConfiguredOrigins() throws Exception {
        mvc.perform(options("/api/v1/production-orders")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
        mvc.perform(options("/api/v1/production-orders")
                        .header("Origin", "https://sitio-malicioso.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
