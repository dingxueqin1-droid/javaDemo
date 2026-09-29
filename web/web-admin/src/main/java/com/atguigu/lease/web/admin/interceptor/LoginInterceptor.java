package com.atguigu.lease.web.admin.interceptor;

import com.atguigu.lease.common.result.ResultCodeEnum;
import com.atguigu.lease.web.admin.exception.LoginException;
import com.atguigu.lease.web.admin.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 浏览器跨域预检不携带 token，交由 Spring 的跨域配置处理。
        if (CorsUtils.isPreFlightRequest(request)) {
            return true;
        }

        String token = request.getHeader("token");
       // String token = "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJsZWFzZS1hZG1pbiIsInN1YiI6IjEiLCJqdGkiOiIzZDkyMzdkYi00ZjgzLTQ5NzUtOWVmYy03N2RhYTZmYTI4NmYiLCJ1c2VySWQiOjEsInVzZXJuYW1lIjoiYWRtaW4iLCJpYXQiOjE3OTA2NTE4NzIsImV4cCI6MTc5MDY1OTA3Mn0.wy_3FljfVX69pUCrc44F0Ix1CEprfHfsnNi5hvfFpxE";

        if (!StringUtils.hasText(token)) {
            String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (StringUtils.hasText(authorization)) {
                if (!authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
                    throw new LoginException(ResultCodeEnum.TOKEN_INVALID);
                }
                token = authorization.substring(7);
            }
        }
        if (!StringUtils.hasText(token)) {
            throw new LoginException(ResultCodeEnum.ADMIN_LOGIN_AUTH);
        }

        Claims claims = jwtUtil.parseToken(token.trim());
        // 用户信息只在本次请求中保存，不会遗留到其他用户的请求。
        request.setAttribute("userId", claims.get("userId", Long.class));
        request.setAttribute("username", claims.get("username", String.class));
        return true;
    }
}
