/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.gate.interceptors;

import static com.netflix.spinnaker.kork.common.Header.REQUEST_ID;

import com.netflix.spinnaker.security.AuthenticatedRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Return values (e.g. X-SPINNAKER-*) stored in the AuthenticatedRequest (backed by MDC and set via
 * com.netflix.spinnaker.filters.AuthenticatedRequestFilter) to gate callers as a response header.
 * For X-SPINNAKER-REQUEST-ID, if its value is absent from AuthenticatedRequest, the value of
 * X-SPINNAKER-EXECUTION-ID is returned as the request ID, or a UUID is generated and returned if
 * X-SPINNAKER-EXECUTION-ID is also absent. For other fields, no values are returned if they are
 * absent from AuthenticatedRequest.
 */
public class ResponseHeaderInterceptor implements HandlerInterceptor {

  private final ResponseHeaderInterceptorConfigurationProperties properties;

  public ResponseHeaderInterceptor(ResponseHeaderInterceptorConfigurationProperties properties) {
    this.properties = properties;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    for (String field : this.properties.getFields()) {
      // getSpinnakerRequestId() contains logic to either return the spinnaker
      // execution id or generate a new one if no current value exists in MDC,
      // the generic get() does not contain such logic
      // check whether we are processing the request id to make sure we call
      // the right method to retain the above logic
      Optional<String> value =
          field.equals(REQUEST_ID.getHeader())
              ? AuthenticatedRequest.getSpinnakerRequestId()
              : AuthenticatedRequest.get(field);
      value.ifPresent(v -> response.setHeader(field, v));
    }
    return true;
  }
}
