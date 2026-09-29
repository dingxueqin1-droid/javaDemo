package com.atguigu.lease.web.admin.service.impl;

import com.atguigu.lease.common.result.ResultCodeEnum;
import com.atguigu.lease.model.entity.SystemUser;
import com.atguigu.lease.model.enums.BaseStatus;
import com.atguigu.lease.web.admin.controller.login.LoginController;
import com.atguigu.lease.web.admin.exception.LoginException;
import com.atguigu.lease.web.admin.exception.LoginExceptionHandler;
import com.atguigu.lease.web.admin.mapper.SystemUserMapper;
import com.atguigu.lease.web.admin.util.JwtUtil;
import com.atguigu.lease.web.admin.vo.login.LoginVo;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SuppressWarnings("unchecked")
class LoginAuthenticationTest {

    private static final String CAPTCHA_KEY = "admin:login:captcha:8f79a4f8-9d7f-4e6a-b654-5085bffb8801";
    // 仅用于测试的签名密钥。
    private static final byte[] TEST_KEY = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    private StringRedisTemplate redisTemplate;
    private SystemUserMapper systemUserMapper;
    private LoginServiceImpl loginService;
    private JwtUtil jwtUtil;
    private LoginVo loginVo;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        systemUserMapper = mock(SystemUserMapper.class);
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", Base64.getEncoder().encodeToString(TEST_KEY));
        ReflectionTestUtils.setField(jwtUtil, "expirationSeconds", 7200L);
        jwtUtil.init();
        loginService = new LoginServiceImpl();
        ReflectionTestUtils.setField(loginService, "redisTemplate", redisTemplate);
        ReflectionTestUtils.setField(loginService, "systemUserMapper", systemUserMapper);
        ReflectionTestUtils.setField(loginService, "jwtUtil", jwtUtil);
        LoginController controller = new LoginController();
        ReflectionTestUtils.setField(controller, "loginService", loginService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new LoginExceptionHandler()).build();

        loginVo = new LoginVo();
        loginVo.setUsername("admin");
        loginVo.setPassword("123456");
        loginVo.setCaptchaKey(CAPTCHA_KEY);
        loginVo.setCaptchaCode("1234");
    }

    @Test
    void loginEndpointReturnsSignedTokenForEnabledAccount() throws Exception {
        givenCaptcha("1234");
        givenUser(BaseStatus.ENABLE);

        String response = mockMvc.perform(post("/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(loginVo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        JsonNode json = new ObjectMapper().readTree(response);
        String token = json.get("data").asText();
        Claims claims = Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(TEST_KEY))
                .requireIssuer("lease-admin").build().parseClaimsJws(token).getBody();
        assertEquals("1", claims.getSubject());
        assertEquals(1L, claims.get("userId", Long.class));
        assertEquals("admin", claims.get("username", String.class));
        assertEquals(7200_000L, claims.getExpiration().getTime() - claims.getIssuedAt().getTime());
        assertNotNull(claims.getId());
        assertFalse(claims.containsKey("password"));
        assertFalse(claims.containsKey("captchaCode"));
        assertThrows(SignatureException.class, () -> Jwts.parserBuilder()
                .setSigningKey(Keys.secretKeyFor(io.jsonwebtoken.SignatureAlgorithm.HS256))
                .build().parseClaimsJws(token));
    }

    @Test
    void missingCaptchaIsRejectedBeforeRedisOrDatabaseAccess() {
        loginVo.setCaptchaCode(" ");
        assertLoginError(ResultCodeEnum.ADMIN_CAPTCHA_CODE_NOT_FOUND);
        verifyNoInteractions(redisTemplate, systemUserMapper);
    }

    @Test
    void unrelatedRedisKeyCannotBeReadOrDeleted() {
        loginVo.setCaptchaKey("some:other:key");
        assertLoginError(ResultCodeEnum.ADMIN_CAPTCHA_CODE_EXPIRED);
        verifyNoInteractions(redisTemplate, systemUserMapper);
    }

    @Test
    void missingOrExpiredCaptchaReturnsBusinessErrorFromEndpoint() throws Exception {
        givenCaptcha(null);
        mockMvc.perform(post("/admin/login").contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(loginVo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(303))
                .andExpect(jsonPath("$.message").value("验证码已过期"))
                .andExpect(jsonPath("$.data").isEmpty());
        verifyNoInteractions(systemUserMapper);
    }

    @Test
    void wrongCaptchaIsRejectedBeforeDatabaseAccess() {
        givenCaptcha("5678");
        assertLoginError(ResultCodeEnum.ADMIN_CAPTCHA_CODE_ERROR);
        verifyNoInteractions(systemUserMapper);
    }

    @Test
    void consumedCaptchaCannotBeReused() {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(CAPTCHA_KEY))))
                .thenReturn("1234", (String) null);
        givenUser(BaseStatus.ENABLE);
        assertNotNull(loginService.login(loginVo));
        assertLoginError(ResultCodeEnum.ADMIN_CAPTCHA_CODE_EXPIRED);
        verify(systemUserMapper, times(1)).selectOne(any(Wrapper.class));
    }

    @Test
    void unknownUsernameIsRejected() {
        givenCaptcha("1234");
        assertLoginError(ResultCodeEnum.ADMIN_ACCOUNT_ERROR);
    }

    @Test
    void wrongPasswordIsRejected() {
        givenCaptcha("1234");
        givenUser(BaseStatus.ENABLE);
        loginVo.setPassword("wrong-password");
        assertLoginError(ResultCodeEnum.ADMIN_ACCOUNT_ERROR);
    }

    @Test
    void blankUsernameOrPasswordCannotTriggerAnUnfilteredQuery() {
        givenCaptcha("1234");
        loginVo.setUsername(" ");
        assertLoginError(ResultCodeEnum.ADMIN_ACCOUNT_ERROR);
        loginVo.setUsername("admin");
        loginVo.setPassword("");
        assertLoginError(ResultCodeEnum.ADMIN_ACCOUNT_ERROR);
        verifyNoInteractions(systemUserMapper);
    }

    @Test
    void disabledAccountCannotLogin() {
        givenCaptcha("1234");
        givenUser(BaseStatus.DISABLE);
        assertLoginError(ResultCodeEnum.ADMIN_ACCOUNT_DISABLED_ERROR);
    }

    @Test
    void unavailableRedisDoesNotBypassCaptcha() {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(CAPTCHA_KEY))))
                .thenThrow(new RedisConnectionFailureException("Redis unavailable"));
        assertThrows(RedisConnectionFailureException.class, () -> loginService.login(loginVo));
        verifyNoInteractions(systemUserMapper);
    }

    @Test
    void nullRequestIsRejected() {
        LoginException exception = assertThrows(LoginException.class, () -> loginService.login(null));
        assertEquals(ResultCodeEnum.PARAM_ERROR, exception.getResultCode());
        verifyNoInteractions(redisTemplate, systemUserMapper);
    }

    @Test
    void weakConfiguredSigningKeyIsRejected() {
        ReflectionTestUtils.setField(jwtUtil, "secret", Base64.getEncoder().encodeToString(new byte[16]));
        assertThrows(WeakKeyException.class, jwtUtil::init);
    }

    @Test
    void loginRequestDoesNotExposeSecretsInDebugLogs() {
        assertFalse(loginVo.toString().contains("password="));
        assertFalse(loginVo.toString().contains("captchaKey="));
        assertFalse(loginVo.toString().contains("captchaCode="));
    }

    @Test
    void developmentKeyCanVerifyTokensUntilRestart() {
        ReflectionTestUtils.setField(jwtUtil, "secret", "");
        jwtUtil.init();
        java.security.Key firstKey = (java.security.Key) ReflectionTestUtils.getField(jwtUtil, "signingKey");
        String token = jwtUtil.createToken(1L, "admin");
        assertEquals("1", Jwts.parserBuilder().setSigningKey(firstKey).build()
                .parseClaimsJws(token).getBody().getSubject());

        jwtUtil.init();
        java.security.Key nextKey = (java.security.Key) ReflectionTestUtils.getField(jwtUtil, "signingKey");
        assertThrows(SignatureException.class, () -> Jwts.parserBuilder().setSigningKey(nextKey).build()
                .parseClaimsJws(token));
    }

    private void givenCaptcha(String code) {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(CAPTCHA_KEY)))).thenReturn(code);
    }

    private void givenUser(BaseStatus status) {
        SystemUser user = new SystemUser();
        user.setId(1L);
        user.setUsername("admin");
        // 已知 MD5 测试向量：123456。
        user.setPassword("e10adc3949ba59abbe56e057f20f883e");
        user.setStatus(status);
        when(systemUserMapper.selectOne(any(Wrapper.class))).thenReturn(user);
    }

    private void assertLoginError(ResultCodeEnum expected) {
        LoginException exception = assertThrows(LoginException.class, () -> loginService.login(loginVo));
        assertEquals(expected, exception.getResultCode());
    }
}
