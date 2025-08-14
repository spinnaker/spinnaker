/*
 * Copyright 2017 Netflix, Inc.
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

import com.netflix.spinnaker.kork.exceptions.UserException;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice
public class GenericExceptionHandlers extends BaseExceptionHandlers {
  private static final Logger logger = LoggerFactory.getLogger(GenericExceptionHandlers.class);

  public GenericExceptionHandlers(ExceptionMessageDecorator exceptionMessageDecorator) {
    super(exceptionMessageDecorator);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public void handleAccessDeniedException(
      Exception e, HttpServletResponse response, HttpServletRequest request) throws IOException {
    storeException(request, response, e);

    // avoid leaking any information that may be in `e.getMessage()` by returning a static error
    // message
    response.sendError(
        HttpStatus.FORBIDDEN.value(), exceptionMessageDecorator.decorate(e, "Access is denied"));
  }

  @ExceptionHandler(NotFoundException.class)
  public void handleNotFoundException(
      Exception e, HttpServletResponse response, HttpServletRequest request) throws IOException {
    storeException(request, response, e);

    response.sendError(
        HttpStatus.NOT_FOUND.value(), exceptionMessageDecorator.decorate(e, e.getMessage()));
  }

  @ExceptionHandler({
    InvalidRequestException.class,
    UserException.class,
    IllegalArgumentException.class
  })
  public void handleInvalidRequestException(
      Exception e, HttpServletResponse response, HttpServletRequest request) throws IOException {
    storeException(request, response, e);
    response.sendError(
        HttpStatus.BAD_REQUEST.value(), exceptionMessageDecorator.decorate(e, e.getMessage()));
  }

  @ExceptionHandler({IllegalStateException.class})
  public void handleIllegalStateException(
      Exception e, HttpServletResponse response, HttpServletRequest request) throws IOException {
    storeException(request, response, e);
    // A subclass of IllegalStateException may have a ResponseStatus annotation
    // (and as of 30-oct-21, AdminController.DiscoveryUnchangeableException in
    // orca does), so handle it, as opposed to calling response.sendError
    // directly.
    handleResponseStatusAnnotatedException(e, response);
  }

  @ExceptionHandler(Exception.class)
  public void handleException(Exception e, HttpServletResponse response, HttpServletRequest request)
      throws IOException {
    logger.warn("Handled error in generic exception handler", e);
    storeException(request, response, e);
    handleResponseStatusAnnotatedException(e, response);
  }

  /**
   * If a ResponseStatus annotation is present on the exception, send the appropriate error message
   * to the response. Otherwise send an internal server error.
   *
   * @param e the exception to process
   * @param response a response
   */
  private void handleResponseStatusAnnotatedException(Exception e, HttpServletResponse response)
      throws IOException {
    ResponseStatus responseStatus =
        AnnotationUtils.findAnnotation(e.getClass(), ResponseStatus.class);

    if (responseStatus != null) {
      HttpStatus httpStatus = responseStatus.value();
      if (httpStatus.is5xxServerError()) {
        logger.error(httpStatus.getReasonPhrase(), e);
      } else if (httpStatus != HttpStatus.NOT_FOUND) {
        logger.error(httpStatus.getReasonPhrase() + ": " + e.toString());
      }

      String message = e.getMessage();
      if (message == null || message.trim().isEmpty()) {
        message = responseStatus.reason();
      }
      response.sendError(httpStatus.value(), exceptionMessageDecorator.decorate(e, message));
    } else {
      logger.error("Internal Server Error", e);
      response.sendError(
          HttpStatus.INTERNAL_SERVER_ERROR.value(),
          exceptionMessageDecorator.decorate(e, e.getMessage()));
    }
  }
}
