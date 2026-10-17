package io.citadel.core.controller;

import io.citadel.core.dto.ErrorResponse;
import io.citadel.core.exception.ApiException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 全局异常 → 统一错误体。不向客户端泄露堆栈与内部信息。 */
@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
    return ResponseEntity.status(ex.status())
        .body(new ErrorResponse(ex.code(), ex.getMessage(), Instant.now()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
    Map<String, String> errors = new LinkedHashMap<>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("code", "VALIDATION_FAILED");
    body.put("message", "请求参数校验失败");
    body.put("timestamp", Instant.now().toString());
    body.put("errors", errors);
    return ResponseEntity.badRequest().body(body);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ErrorResponse> handleConflict(DataIntegrityViolationException ex) {
    log.warn("数据完整性冲突: {}", ex.getMostSpecificCause().getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ErrorResponse("CONFLICT", "数据冲突，请检查唯一性字段", Instant.now()));
  }

  /** 请求体不可读/类型不匹配（如 password 传了字符串而信封要求对象）→ 400，不泄露解析细节。 */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
    return ResponseEntity.badRequest()
        .body(new ErrorResponse("REQUEST_BODY_INVALID", "请求体格式非法", Instant.now()));
  }

  /** 未知路径 → 404（默认会落入兜底 500，语义错误）。 */
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorResponse("NOT_FOUND", "接口不存在", Instant.now()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
    log.error("未处理异常", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ErrorResponse("INTERNAL_ERROR", "服务内部错误", Instant.now()));
  }
}
