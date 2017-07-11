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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.web.DefaultErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@ControllerAdvice
public class GenericExceptionHandlers {
  private static final Logger logger = LoggerFactory.getLogger(GenericExceptionHandlers.class);

  private final DefaultErrorAttributes defaultErrorAttributes = new DefaultErrorAttributes();

  @ExceptionHandler(NotFoundException.class)
  public void handleNotFoundException(Exception e, HttpServletResponse response, HttpServletRequest request) throws IOException {
    storeException(request, response, e);
    response.sendError(HttpStatus.NOT_FOUND.value(), e.getMessage());
  }

  @ExceptionHandler(Exception.class)
  public void handleException(Exception e, HttpServletResponse response, HttpServletRequest request) throws IOException {
    logger.error("Internal Server Error", e);

    storeException(request, response, e);

    ResponseStatus responseStatus = e.getClass().getAnnotation(ResponseStatus.class);
    if (responseStatus != null) {
      String message = e.getMessage();
      if (message == null || message.trim().isEmpty()) {
        message = responseStatus.reason();
      }
      response.sendError(responseStatus.value().value(), message);
    } else {
      response.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value(), e.getMessage());
    }

  }

  private void storeException(HttpServletRequest request, HttpServletResponse response, Exception ex) {
    // store exception as an attribute of HttpServletRequest such that it can be referenced by GenericErrorController
    defaultErrorAttributes.resolveException(request, response, null, ex);
  }
}
