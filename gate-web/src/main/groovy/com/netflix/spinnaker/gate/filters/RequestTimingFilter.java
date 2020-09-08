/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.spinnaker.gate.filters;

import com.netflix.spinnaker.kork.common.Header;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.io.IOException;
import java.util.UUID;
import javax.servlet.*;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.MDC;

/**
 * An HttpFilter that generates request start time and request id values.
 *
 * <p>The generation has been extracted from {@see RequestLoggingFilter} to allow for it to happen
 * earlier in the request lifecycle.
 *
 * <p>It is expected that this filter will be given the highest precedence and run prior to the
 * security filter chain, thus including the time spent authenticating in the overall request
 * duration.
 *
 * <p>RequestTimingFilter -> Security Filter -> AuthenticatedRequestFilter -> RequestLoggingFilter
 */
public class RequestTimingFilter extends HttpFilter {
  static String REQUEST_START_TIME = "requestStartTime";

  @Override
  protected void doFilter(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    AuthenticatedRequest.set(Header.REQUEST_ID.getHeader(), UUID.randomUUID().toString());
    MDC.put(REQUEST_START_TIME, String.valueOf(System.currentTimeMillis()));

    try {
      chain.doFilter(request, response);
    } finally {
      AuthenticatedRequest.clear();
      MDC.remove(REQUEST_START_TIME);
    }
  }
}
