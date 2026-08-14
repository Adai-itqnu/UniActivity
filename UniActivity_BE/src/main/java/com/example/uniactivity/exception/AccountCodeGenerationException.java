package com.example.uniactivity.exception;

public class AccountCodeGenerationException extends RuntimeException {

    public AccountCodeGenerationException() {
        super("Không thể tạo mã tài khoản. Vui lòng thử lại.");
    }
}
