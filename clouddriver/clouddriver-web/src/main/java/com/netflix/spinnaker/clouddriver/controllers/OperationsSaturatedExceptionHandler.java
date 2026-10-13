/*
 * Copyright 2026 McIntosh.farm
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.controllers;

import com.netflix.spinnaker.clouddriver.orchestration.OperationsSaturatedException;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Adds {@code Retry-After} to the 503 for a saturated instance. Kork's catch-all handler would
 * otherwise answer the same status without the header.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OperationsSaturatedExceptionHandler {

  @ExceptionHandler(OperationsSaturatedException.class)
  public ResponseEntity<Map<String, Object>> handle(OperationsSaturatedException e) {
    HttpStatus status = HttpStatus.SERVICE_UNAVAILABLE;
    return ResponseEntity.status(status)
        .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()))
        .body(
            Map.of(
                "error", status.getReasonPhrase(),
                "message", e.getMessage(),
                "status", status.value()));
  }
}
