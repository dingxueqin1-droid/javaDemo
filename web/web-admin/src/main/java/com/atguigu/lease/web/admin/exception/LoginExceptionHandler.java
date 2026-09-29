package com.atguigu.lease.web.admin.exception;

import com.atguigu.lease.common.result.Result;
import com.atguigu.lease.web.admin.controller.login.LoginController;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = LoginController.class)
public class LoginExceptionHandler {

    @ExceptionHandler(LoginException.class)
    public Result<Void> handleLoginException(LoginException exception) {
        return Result.build(null, exception.getResultCode());
    }
}
