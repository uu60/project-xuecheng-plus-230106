package com.xuecheng.base.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // 此类异常是程序员主动抛出的异常，是可预知异常
    @ExceptionHandler(XueChengPlusException.class) // 此方法捕获value值的异常
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public RestErrorResponse doXueChengException(XueChengPlusException e) {
        log.error("捕获异常: {}", e.getMessage());
        e.printStackTrace();
        String message = e.getMessage();
        return new RestErrorResponse(message);
    }

    // 捕获不可预知的异常
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public RestErrorResponse doException(Exception e) {
        log.error("捕获异常: {}", e.getMessage());
        e.printStackTrace();
        return new RestErrorResponse(CommonError.UNKOWN_ERROR.getErrMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public RestErrorResponse doMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        BindingResult bindingResult = e.getBindingResult();
        List<FieldError> fieldErrors = bindingResult.getFieldErrors();
        StringBuilder errors = new StringBuilder();
        fieldErrors.forEach(error -> {
            errors.append(error.getDefaultMessage()).append(", ");
        });
        errors.delete(errors.lastIndexOf(", "), errors.length());
        return new RestErrorResponse(errors.toString());
    }
}
