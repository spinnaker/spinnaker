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

import java.io.IOException;
import java.util.StringJoiner;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class FiatAccessDeniedExceptionHandler {

  private final DefaultErrorAttributes defaultErrorAttributes = new DefaultErrorAttributes();

  @ExceptionHandler(AccessDeniedException.class)
  public void handleAccessDeniedException(
      AccessDeniedException e, HttpServletResponse response, HttpServletRequest request)
      throws IOException {
    storeException(request, response, e);
    String errorMessage =
        FiatPermissionEvaluator.getAuthorizationFailure()
            .map(this::authorizationFailureMessage)
            .orElse("Access is denied");

    response.sendError(HttpStatus.FORBIDDEN.value(), errorMessage);
  }

  private String authorizationFailureMessage(
      FiatPermissionEvaluator.AuthorizationFailure authorizationFailure) {
    StringJoiner sj =
        new StringJoiner(" ")
            .add("Access denied to")
            .add(authorizationFailure.getResourceType().modelClass.getSimpleName().toLowerCase())
            .add(authorizationFailure.getResourceName());

    if (authorizationFailure.hasAuthorization()) {
      sj.add("- required authorization:").add(authorizationFailure.getAuthorization().toString());
    }

    return sj.toString();
  }

  private void storeException(
      HttpServletRequest request, HttpServletResponse response, Exception ex) {
    // store exception as an attribute of HttpServletRequest such that it can be referenced by
    // GenericErrorController
    defaultErrorAttributes.resolveException(request, response, null, ex);
  }
}
