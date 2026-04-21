/*
 * Copyright 2021 Salesforce, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.kork.web.exceptions;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

public class BaseExceptionHandlers extends ResponseEntityExceptionHandler {
  private static final Logger logger = LoggerFactory.getLogger(BaseExceptionHandlers.class);

  private final DefaultErrorAttributes defaultErrorAttributes = new DefaultErrorAttributes();

  protected final ExceptionMessageDecorator exceptionMessageDecorator;

  public BaseExceptionHandlers(ExceptionMessageDecorator exceptionMessageDecorator) {
    this.exceptionMessageDecorator = exceptionMessageDecorator;
  }

  /**
   * Handle {@link ResponseStatusException} by routing it through {@code response.sendError()},
   * producing the standard Spinnaker error format (timestamp, status, error, exception, message).
   *
   * <p>Without this handler, {@link ResponseStatusException} is handled by the inherited {@link
   * ResponseEntityExceptionHandler#handleErrorResponseException}, which produces RFC 7807 Problem
   * Details (type, title, status, detail, instance) instead.
   */
  @ExceptionHandler(ResponseStatusException.class)
  public void handleResponseStatusException(
      ResponseStatusException e, HttpServletResponse response, HttpServletRequest request)
      throws IOException {
    storeException(request, response, e);
    HttpStatusCode statusCode = e.getStatusCode();
    String reason = StringUtils.hasText(e.getReason()) ? e.getReason() : e.getMessage();
    if (statusCode.is5xxServerError()) {
      logger.error(reason, e);
    } else if (statusCode != HttpStatus.NOT_FOUND) {
      logger.error("{}: {}", reason, e.toString());
    }
    response.sendError(statusCode.value(), exceptionMessageDecorator.decorate(e, reason));
  }

  protected void storeException(
      HttpServletRequest request, HttpServletResponse response, Exception ex) {
    // store exception as an attribute of HttpServletRequest such that it can be referenced by
    // GenericErrorController
    defaultErrorAttributes.resolveException(request, response, null, ex);
  }
}
