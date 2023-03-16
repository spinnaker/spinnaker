/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.fiat.shared;

import com.netflix.spinnaker.kork.api.exceptions.AccessDeniedDetails;
import com.netflix.spinnaker.kork.web.exceptions.ExceptionMessageDecorator;
import java.io.IOException;
import java.util.Map;
import java.util.StringJoiner;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class FiatAccessDeniedExceptionHandler {
  private final Logger log = LoggerFactory.getLogger(getClass());

  private final DefaultErrorAttributes defaultErrorAttributes = new DefaultErrorAttributes();
  private final HeadersRedactor headersRedactor = new HeadersRedactor();

  private final ExceptionMessageDecorator exceptionMessageDecorator;

  public FiatAccessDeniedExceptionHandler(ExceptionMessageDecorator exceptionMessageDecorator) {
    this.exceptionMessageDecorator = exceptionMessageDecorator;
  }

  @ExceptionHandler(AccessDeniedException.class)
  public void handleAccessDeniedException(
      AccessDeniedException e, HttpServletResponse response, HttpServletRequest request)
      throws IOException {
    storeException(request, response, e);

    Map<String, String> headers = headersRedactor.getRedactedHeaders(request);

    log.error(
        "Encountered exception while processing request {}:{} with headers={}",
        request.getMethod(),
        request.getRequestURI(),
        headers.toString(),
        e);

    String errorMessage =
        FiatPermissionEvaluator.getAuthorizationFailure()
            .map(authorizationFailure -> authorizationFailureMessage(authorizationFailure, e))
            .orElse("Access is denied");

    response.sendError(HttpStatus.FORBIDDEN.value(), errorMessage);
  }

  private String authorizationFailureMessage(
      FiatPermissionEvaluator.AuthorizationFailure authorizationFailure, AccessDeniedException e) {
    StringJoiner sj = new StringJoiner(" ");

    defaultErrorDecoration(sj, authorizationFailure);

    // TODO(jonsie): Once we have migrated the current fiat-api code to kork-authz we can produce a
    //  proper fiat-api module that will allow us to export a class like the one below (instead
    //  of the current hack which is to pull this in from kork-api).
    AccessDeniedDetails accessDeniedDetails =
        new AccessDeniedDetails(
            authorizationFailure.getResourceType().toString(),
            authorizationFailure.getResourceName(),
            authorizationFailure.hasAuthorization()
                ? authorizationFailure.getAuthorization().toString()
                : null);

    return exceptionMessageDecorator.decorate(e, sj.toString(), accessDeniedDetails);
  }

  /**
   * Default access denied error decoration - decorates the error with the resource type and
   * resource name that authorization was denied to.
   */
  private void defaultErrorDecoration(
      StringJoiner sj, FiatPermissionEvaluator.AuthorizationFailure authorizationFailure) {
    // Make the resource type readable (ie, "service account" instead of "serviceaccount")
    String resourceType =
        authorizationFailure.getResourceType().toString().replace("_", " ").toLowerCase();

    sj.add("Access denied to").add(resourceType).add(authorizationFailure.getResourceName());

    if (authorizationFailure.hasAuthorization()) {
      sj.add("- required authorization:").add(authorizationFailure.getAuthorization().toString());
    }
  }

  private void storeException(
      HttpServletRequest request, HttpServletResponse response, Exception ex) {
    // store exception as an attribute of HttpServletRequest such that it can be referenced by
    // GenericErrorController
    defaultErrorAttributes.resolveException(request, response, null, ex);
  }
}
