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

package com.netflix.spinnaker.kork.retrofit.exceptions;

import com.netflix.spinnaker.kork.web.exceptions.BaseExceptionHandlers;
import com.netflix.spinnaker.kork.web.exceptions.ExceptionMessageDecorator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Give this controller advice precedence over GenericExceptionHandlers in kork-web. An alternative
 * is to teach GenericExceptionHandlers to handle e.g. SpinnakerServerException, but that creates a
 * circular dependency. kork-retrofit already depends on kork-web, and for GenericExceptionHandlers
 * to handle SpinnakerServerException, kork-web would need to depend on kork-retrofit.
 */
@ControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE - 1)
public class SpinnakerRetrofitExceptionHandlers extends BaseExceptionHandlers {
  private static final Logger logger =
      LoggerFactory.getLogger(SpinnakerRetrofitExceptionHandlers.class);

  public SpinnakerRetrofitExceptionHandlers(ExceptionMessageDecorator exceptionMessageDecorator) {
    super(exceptionMessageDecorator);
  }

  @ExceptionHandler({SpinnakerServerException.class})
  public void handleSpinnakerServerException(
      SpinnakerServerException e, HttpServletResponse response, HttpServletRequest request)
      throws IOException {
    storeException(request, response, e);
    logger.error(e.getMessage(), e);
    response.sendError(
        HttpStatus.INTERNAL_SERVER_ERROR.value(),
        exceptionMessageDecorator.decorate(e, e.getMessage()));
  }

  @ExceptionHandler({SpinnakerHttpException.class})
  public void handleSpinnakerHttpException(
      SpinnakerHttpException e, HttpServletResponse response, HttpServletRequest request)
      throws IOException {
    // We made an http request that failed and nothing else handled that
    // failure, so generate our response based on the response we received.
    storeException(request, response, e);
    int status = e.getResponseCode();
    // Log server errors as errors, but client errors as debug to avoid filling
    // up the logs with someone else's problem.
    HttpStatus httpStatus = HttpStatus.resolve(status);
    if (httpStatus.is5xxServerError()) {
      logger.error(e.getMessage(), e);
    } else {
      logger.debug(e.getMessage());
    }
    response.sendError(status, exceptionMessageDecorator.decorate(e, e.getMessage()));
  }
}
