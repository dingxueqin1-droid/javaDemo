package com.atguigu.lease.web.admin.exception;

import com.atguigu.lease.common.result.ResultCodeEnum;
import lombok.Getter;

@Getter
public class LoginException extends RuntimeException {

    private final ResultCodeEnum resultCode;

    public LoginException(ResultCodeEnum resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
    }
}
