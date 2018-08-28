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

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@ControllerAdvice
public class FiatAccessDeniedExceptionHandler {
  @ExceptionHandler(AccessDeniedException.class)
  public void handleAccessDeniedException(AccessDeniedException e,
                                          HttpServletResponse response,
                                          HttpServletRequest request) throws IOException {
    String errorMessage = FiatPermissionEvaluator.getAuthorizationFailure().map(a ->
        "Access denied to " + a.getResourceType().modelClass.getSimpleName().toLowerCase() + " " + a.getResourceName()
    ).orElse("Access is denied");

    response.sendError(HttpStatus.FORBIDDEN.value(), errorMessage);
  }
}
