package com.example.uniactivity.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateException extends RuntimeException {
    public DuplicateException(String message) {
        super(message);
    }
    
    public DuplicateException(String field, String value) {
        super(String.format("%s đã tồn tại: %s", field, value));
    }
}
