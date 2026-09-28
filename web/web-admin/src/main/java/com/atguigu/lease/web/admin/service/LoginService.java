package com.atguigu.lease.web.admin.service;

import com.atguigu.lease.web.admin.vo.login.CaptchaVo;
import com.atguigu.lease.web.admin.vo.login.LoginVo;
import com.atguigu.lease.web.admin.vo.system.user.SystemUserInfoVo;

public interface LoginService {

    /**
     * 生成图形验证码，将答案缓存到 Redis，并返回图片和缓存 key。
     */
    CaptchaVo getCaptcha();
}
