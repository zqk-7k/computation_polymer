package com.vaspshow.backend.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ErrorResponse> handleApi(ApiException ex, HttpServletRequest request) {
    HttpStatus status = ex.getStatus();
    return ResponseEntity.status(status).body(new ErrorResponse(
        Instant.now(),
        status.value(),
        status.getReasonPhrase(),
        ex.getMessage(),
        request.getRequestURI()
    ));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
    log.error("Unexpected API error", ex);
    HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
    return ResponseEntity.status(status).body(new ErrorResponse(
        Instant.now(),
        status.value(),
        status.getReasonPhrase(),
        "服务端读取数据失败",
        request.getRequestURI()
    ));
  }
}
