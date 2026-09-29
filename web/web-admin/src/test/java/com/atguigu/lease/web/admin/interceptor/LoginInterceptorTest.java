package com.atguigu.lease.web.admin.interceptor;

import com.atguigu.lease.common.result.Result;
import com.atguigu.lease.web.admin.config.WebMvcConfiguration;
import com.atguigu.lease.web.admin.controller.login.LoginController;
import com.atguigu.lease.web.admin.exception.LoginExceptionHandler;
import com.atguigu.lease.web.admin.service.LoginService;
import com.atguigu.lease.web.admin.util.JwtUtil;
import com.atguigu.lease.web.admin.vo.login.CaptchaVo;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig(LoginInterceptorTest.TestConfig.class)
@TestPropertySource(properties = {
        "lease.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "lease.jwt.expiration-seconds=7200"
})
class LoginInterceptorTest {

    private static final byte[] TEST_KEY = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtUtil jwtUtil;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // 加载真实 MVC 配置，验证拦截路径及排除路径确实生效。
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/probe", "/admin/info", "/admin/login/extra"})
    void missingTokenCannotAccessProtectedEndpoints(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(305))
                .andExpect(jsonPath("$.message").value("未登陆"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void validTokenHeaderAllowsRequestAndExposesVerifiedIdentity() throws Exception {
        mockMvc.perform(get("/admin/probe").header("token", jwtUtil.createToken(42L, "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userId").value(42))
                .andExpect(jsonPath("$.data.username").value("admin"));
    }

    @Test
    void bearerTokenAlsoAllowsRequest() throws Exception {
        mockMvc.perform(get("/admin/probe")
                        .header("Authorization", "Bearer " + jwtUtil.createToken(7L, "user")))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userId").value(7));
    }

    @Test
    void identityDoesNotLeakIntoNextRequest() throws Exception {
        mockMvc.perform(get("/admin/probe").header("token", jwtUtil.createToken(42L, "admin")))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/admin/probe"))
                .andExpect(jsonPath("$.code").value(305));
    }

    @ParameterizedTest
    @MethodSource("invalidTokens")
    void invalidTokensAreRejected(String token) throws Exception {
        mockMvc.perform(get("/admin/probe").header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(602))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    static Stream<String> invalidTokens() {
        return Stream.of(
                "not-a-jwt",
                tokenBuilder().signWith(Keys.secretKeyFor(SignatureAlgorithm.HS256)).compact(),
                tokenBuilder().compact(),
                tokenBuilder().setIssuer("other-app").signWith(Keys.hmacShaKeyFor(TEST_KEY)).compact(),
                tokenBuilder().setExpiration(null).signWith(Keys.hmacShaKeyFor(TEST_KEY)).compact(),
                tokenBuilder().claim("userId", null).signWith(Keys.hmacShaKeyFor(TEST_KEY)).compact(),
                tokenBuilder().setSubject("99").signWith(Keys.hmacShaKeyFor(TEST_KEY)).compact());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        String token = tokenBuilder().setExpiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(TEST_KEY)).compact();
        mockMvc.perform(get("/admin/probe").header("token", token))
                .andExpect(jsonPath("$.code").value(601))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void tokenInQueryStringDoesNotAuthenticateRequest() throws Exception {
        mockMvc.perform(get("/admin/probe").param("token", jwtUtil.createToken(1L, "admin")))
                .andExpect(jsonPath("$.code").value(305));
    }

    @Test
    void loginAndCaptchaRemainAccessibleEvenWithAnOldInvalidToken() throws Exception {
        mockMvc.perform(post("/admin/login").header("token", "expired-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("login-result"));
        mockMvc.perform(get("/admin/login/captcha").header("token", "expired-token"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.key").value("captcha-key"));
    }

    @Test
    void documentationIsOutsideAdminInterceptorScope() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string("docs"));
    }

    @Test
    void corsPreflightDoesNotRequireToken() throws Exception {
        mockMvc.perform(options("/admin/probe").header("Origin", "https://example.test")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://example.test"));
    }

    @Test
    void ordinaryOptionsRequestIsStillProtected() throws Exception {
        mockMvc.perform(options("/admin/probe"))
                .andExpect(jsonPath("$.code").value(305));
    }

    private static JwtBuilder tokenBuilder() {
        return Jwts.builder().setIssuer("lease-admin").setSubject("1")
                .claim("userId", 1L).claim("username", "admin")
                .setExpiration(Date.from(Instant.now().plusSeconds(3600)));
    }

    @Configuration
    @EnableWebMvc
    @Import({WebMvcConfiguration.class, LoginInterceptor.class, JwtUtil.class,
            LoginExceptionHandler.class, LoginController.class, ProbeController.class})
    static class TestConfig implements WebMvcConfigurer {

        @Bean
        LoginService loginService() {
            LoginService service = mock(LoginService.class);
            when(service.login(any())).thenReturn("login-result");
            when(service.getCaptcha()).thenReturn(new CaptchaVo("image", "captcha-key"));
            return service;
        }

        @Override
        public void addCorsMappings(CorsRegistry registry) {
            registry.addMapping("/admin/**").allowedOrigins("https://example.test")
                    .allowedMethods("GET", "POST");
        }
    }

    @RestController
    static class ProbeController {

        @GetMapping({"/admin/probe", "/admin/login/extra"})
        public Result<Map<String, Object>> protectedEndpoint(HttpServletRequest request) {
            return Result.ok(Map.of("userId", request.getAttribute("userId"),
                    "username", request.getAttribute("username")));
        }

        @GetMapping("/v3/api-docs")
        public String docs() {
            return "docs";
        }
    }
}
