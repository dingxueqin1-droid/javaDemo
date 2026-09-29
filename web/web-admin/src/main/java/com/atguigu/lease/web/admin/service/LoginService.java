package com.atguigu.lease.web.admin.service;

import com.atguigu.lease.web.admin.vo.login.CaptchaVo;
import com.atguigu.lease.web.admin.vo.login.LoginVo;

public interface LoginService {

    /**
     * 生成图形验证码，将答案缓存到 Redis，并返回图片和缓存 key。
     */
    CaptchaVo getCaptcha();

    /**
     * 校验验证码和账号密码，返回登录 token。
     */
    String login(LoginVo loginVo);
}
