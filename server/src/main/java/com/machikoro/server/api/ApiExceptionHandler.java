package com.machikoro.server.api;

import com.machikoro.server.api.LobbyDtos.ErrorMessage;
import com.machikoro.server.game.LobbyError;
import com.machikoro.server.game.LobbyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * REST errors as {@code {code, message}} with a matching HTTP status.
 */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(LobbyException.class)
    ResponseEntity<ErrorMessage> lobby(LobbyException e) {
        return error(e.code(), e.getMessage());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ErrorMessage> missingToken(MissingRequestHeaderException e) {
        return error(LobbyError.INVALID_TOKEN, "Header " + e.getHeaderName() + " is required");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorMessage> invalid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .findFirst()
                .orElse("Invalid request");
        return error(LobbyError.BAD_REQUEST, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorMessage> unreadable(HttpMessageNotReadableException e) {
        return error(LobbyError.BAD_REQUEST, "Malformed request body");
    }

    private static ResponseEntity<ErrorMessage> error(LobbyError code, String message) {
        return ResponseEntity.status(HttpStatus.valueOf(code.httpStatus())).body(new ErrorMessage(code.name(), message));
    }
}
