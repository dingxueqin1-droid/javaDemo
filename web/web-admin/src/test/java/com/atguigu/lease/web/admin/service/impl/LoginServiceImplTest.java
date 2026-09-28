package com.atguigu.lease.web.admin.service.impl;

import com.atguigu.lease.web.admin.controller.login.LoginController;
import com.atguigu.lease.web.admin.vo.login.CaptchaVo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LoginServiceImplTest {

    private ValueOperations<String, String> valueOperations;
    private LoginServiceImpl loginService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        loginService = new LoginServiceImpl();
        ReflectionTestUtils.setField(loginService, "redisTemplate", redisTemplate);
    }

    @Test
    void endpointReturnsPngAndKeyAndCachesAnswerWithExpiry() throws Exception {
        LoginController loginController = new LoginController();
        ReflectionTestUtils.setField(loginController, "loginService", loginService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(loginController).build();
        String response = mockMvc.perform(get("/admin/login/captcha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.code").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = new ObjectMapper().readTree(response).get("data");
        String key = data.get("key").asText();
        String image = data.get("image").asText();
        assertEquals(2, data.size());
        assertTrue(key.startsWith("admin:login:captcha:"));
        assertTrue(image.startsWith("data:image/png;base64,"));

        byte[] png = Base64.getDecoder().decode(image.substring(image.indexOf(',') + 1));
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(png));
        assertNotNull(decoded);
        assertEquals(130, decoded.getWidth());
        assertEquals(48, decoded.getHeight());

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq(key), code.capture(), eq(Duration.ofSeconds(60)));
        assertTrue(code.getValue().matches("[0-9]{4}"));
    }

    @Test
    void separateRequestsHaveDistinctCacheKeys() {
        CaptchaVo first = loginService.getCaptcha();
        CaptchaVo second = loginService.getCaptcha();

        assertNotEquals(first.getKey(), second.getKey());
        verify(valueOperations).set(eq(first.getKey()), anyString(), eq(Duration.ofSeconds(60)));
        verify(valueOperations).set(eq(second.getKey()), anyString(), eq(Duration.ofSeconds(60)));
    }

    @Test
    void cacheFailureDoesNotReturnAnUnusableCaptcha() {
        doThrow(new RedisConnectionFailureException("Redis unavailable"))
                .when(valueOperations).set(anyString(), anyString(), any(Duration.class));

        assertThrows(RedisConnectionFailureException.class, loginService::getCaptcha);
    }
}
