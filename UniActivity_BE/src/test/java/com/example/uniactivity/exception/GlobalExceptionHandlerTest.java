package com.example.uniactivity.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void authorizationReturns403() {
        assertEquals(HttpStatus.FORBIDDEN,
                handler.handleAuthorization(new AuthorizationException("forbidden"))
                        .getStatusCode());
    }

    @Test
    void conflictReturns409() {
        assertEquals(HttpStatus.CONFLICT,
                handler.handleConflict(new ConflictException("conflict")).getStatusCode());
        assertEquals(HttpStatus.CONFLICT,
                handler.handleDataIntegrity(
                                new DataIntegrityViolationException("duplicate internal value"))
                        .getStatusCode());
    }

    @Test
    void validationReturns400() {
        assertEquals(HttpStatus.BAD_REQUEST,
                handler.handleValidation(new ValidationException("invalid")).getStatusCode());
    }

    @Test
    void wrappedIOExceptionReturns500WithoutLeakingDetails() {
        var response = handler.handleGeneral(
                new IllegalStateException("wrapper", new IOException("secret path")));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Lỗi hệ thống, vui lòng thử lại sau", response.getBody().getMessage());
    }

    @Test
    void asyncDisconnectDoesNotBuildAnotherResponse() {
        assertDoesNotThrow(() -> handler.handleAsyncDisconnect(
                new AsyncRequestNotUsableException("client disconnected")));
    }
}
