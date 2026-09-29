package com.atguigu.lease.web.admin.service.impl;

import com.atguigu.lease.common.result.ResultCodeEnum;
import com.atguigu.lease.model.entity.SystemUser;
import com.atguigu.lease.model.enums.BaseStatus;
import com.atguigu.lease.web.admin.exception.LoginException;
import com.atguigu.lease.web.admin.mapper.SystemUserMapper;
import com.atguigu.lease.web.admin.service.LoginService;
import com.atguigu.lease.web.admin.util.JwtUtil;
import com.atguigu.lease.web.admin.vo.login.CaptchaVo;
import com.atguigu.lease.web.admin.vo.login.LoginVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wf.captcha.SpecCaptcha;
import com.wf.captcha.base.Captcha;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

@Service
public class LoginServiceImpl implements LoginService {

    private static final String CAPTCHA_KEY_PREFIX = "admin:login:captcha:";
    private static final Duration CAPTCHA_TTL = Duration.ofMinutes(5);
    // 原子地读取并删除验证码，避免同一验证码被并发请求重复使用。
    private static final DefaultRedisScript<String> CONSUME_CAPTCHA_SCRIPT = new DefaultRedisScript<>(
            "local code = redis.call('GET', KEYS[1]); "
                    + "redis.call('DEL', KEYS[1]); return code", String.class);

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private SystemUserMapper systemUserMapper;

    @Autowired
    private JwtUtil jwtUtil;

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

    @Override
    public String login(LoginVo loginVo) {
        if (loginVo == null) {
            throw new LoginException(ResultCodeEnum.PARAM_ERROR);
        }
        // 1. 校验验证码；key 必须来自获取验证码接口。
        if (!StringUtils.hasText(loginVo.getCaptchaCode())) {
            throw new LoginException(ResultCodeEnum.ADMIN_CAPTCHA_CODE_NOT_FOUND);
        }
        String captchaKey = loginVo.getCaptchaKey();
        if (!StringUtils.hasText(captchaKey) || !captchaKey.startsWith(CAPTCHA_KEY_PREFIX)) {
            throw new LoginException(ResultCodeEnum.ADMIN_CAPTCHA_CODE_EXPIRED);
        }
        String captchaCode = redisTemplate.execute(CONSUME_CAPTCHA_SCRIPT,
                Collections.singletonList(captchaKey));
        if (captchaCode == null) {
            throw new LoginException(ResultCodeEnum.ADMIN_CAPTCHA_CODE_EXPIRED);
        }
        if (!captchaCode.equalsIgnoreCase(loginVo.getCaptchaCode().trim())) {
            throw new LoginException(ResultCodeEnum.ADMIN_CAPTCHA_CODE_ERROR);
        }

        // 2. 按用户名查询未被逻辑删除的账号，并校验数据库已有的 MD5 密码。
        if (!StringUtils.hasText(loginVo.getUsername()) || !StringUtils.hasText(loginVo.getPassword())) {
            throw new LoginException(ResultCodeEnum.ADMIN_ACCOUNT_ERROR);
        }
        SystemUser user = systemUserMapper.selectOne(new LambdaQueryWrapper<SystemUser>()
                .eq(SystemUser::getUsername, loginVo.getUsername()));
        if (user == null) {
            throw new LoginException(ResultCodeEnum.ADMIN_ACCOUNT_ERROR);
        }
        String password = DigestUtils.md5DigestAsHex(loginVo.getPassword().getBytes(StandardCharsets.UTF_8));
        if (!password.equalsIgnoreCase(user.getPassword())) {
            throw new LoginException(ResultCodeEnum.ADMIN_ACCOUNT_ERROR);
        }
        if (user.getStatus() != BaseStatus.ENABLE) {
            throw new LoginException(ResultCodeEnum.ADMIN_ACCOUNT_DISABLED_ERROR);
        }

        // 3. 签发 token，内容仅包含身份信息，不包含密码和验证码。
        return jwtUtil.createToken(user.getId(), user.getUsername());
    }
}
