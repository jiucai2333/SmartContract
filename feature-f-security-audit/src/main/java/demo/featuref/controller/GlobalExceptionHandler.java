package demo.featuref.controller;

import demo.featuref.common.Result;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({
            IllegalArgumentException.class,
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class
    })
    public Result<Object> badRequest(Exception ex) {
        return Result.error(400, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<Object> serverError(Exception ex) {
        return Result.error(500, ex.getMessage());
    }
}
