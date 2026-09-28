package com.atguigu.lease.web.admin.service.impl;

import com.atguigu.lease.web.admin.service.LoginService;
import com.atguigu.lease.web.admin.vo.login.CaptchaVo;
import com.wf.captcha.SpecCaptcha;
import com.wf.captcha.base.Captcha;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class LoginServiceImpl implements LoginService {

    private static final String CAPTCHA_KEY_PREFIX = "admin:login:captcha:";
    private static final Duration CAPTCHA_TTL = Duration.ofSeconds(60);

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public CaptchaVo getCaptcha() {
        SpecCaptcha captcha = new SpecCaptcha(130, 48, 4);
        captcha.setCharType(Captcha.TYPE_ONLY_NUMBER);
        String code = captcha.text();
        String image = captcha.toBase64();
        String key = CAPTCHA_KEY_PREFIX + UUID.randomUUID();

        // 写入答案时同时设置过期时间，前端仅接收图片和 key。
        redisTemplate.opsForValue().set(key, code, CAPTCHA_TTL);
        return new CaptchaVo(image, key);
    }
}
