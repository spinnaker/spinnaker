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

import static net.logstash.logback.argument.StructuredArguments.value;

import com.netflix.spinnaker.kork.common.Header;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import javax.servlet.*;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * An HttpFilter that generates request start time/id values and logs standard request output.
 *
 * <p>ex. 127.0.0.1 "GET /limecat.jpg HTTP/1.0" 200 2326
 *
 * <p>It is expected that this filter will be given the highest precedence and run prior to the
 * security filter chain, thus including the time spent authenticating in the overall request
 * duration.
 *
 * <p>RequestLoggingFilter -> Security Filter -> AuthenticatedRequestFilter
 */
public class RequestLoggingFilter extends HttpFilter {
  public static String REQUEST_START_TIME = "requestStartTime";

  private Logger log = LoggerFactory.getLogger(getClass());

  @Override
  protected void doFilter(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    Long requestStartTime = System.currentTimeMillis();

    try {
      AuthenticatedRequest.set(Header.REQUEST_ID.getHeader(), UUID.randomUUID().toString());
      MDC.put(REQUEST_START_TIME, String.valueOf(requestStartTime));

      chain.doFilter(request, response);
    } finally {
      try {
        MDC.put("requestDuration", getRequestDuration(requestStartTime));
        MDC.put("requestUserAgent", request.getHeader("User-Agent"));
        MDC.put("requestPort", String.valueOf(request.getServerPort()));

        // 127.0.0.1 "GET /limecat.jpg HTTP/1.0" 200 2326
        log.debug(
            "{} \"{} {} {}\" {} {}",
            value("sourceIp", sourceIpAddress(request)),
            value("requestMethod", request.getMethod()),
            value("requestEndpoint", getRequestEndpoint(request)),
            value("requestProtocol", request.getProtocol()),
            value("responseStatus", response.getStatus()),
            value("responseSize", getResponseSize(response)));
      } finally {
        MDC.remove("requestDuration");
        MDC.remove("requestUserAgent");
        MDC.remove("requestPort");
        MDC.remove(REQUEST_START_TIME);
      }
    }
  }

  private static String getRequestDuration(Long startTime) {
    if (startTime == null) {
      return "unknown";
    }
    return (System.currentTimeMillis() - startTime) + "ms";
  }

  private static String sourceIpAddress(HttpServletRequest request) {
    String ip = request.getHeader("X-FORWARDED-FOR");
    if (ip == null) {
      return request.getRemoteAddr();
    }
    return ip;
  }

  private static String getRequestEndpoint(HttpServletRequest request) {
    String endpoint = request.getRequestURI();
    if (request.getQueryString() != null) {
      return endpoint + "?" + request.getQueryString();
    }
    return endpoint;
  }

  private static int getResponseSize(HttpServletResponse response) {
    return Optional.ofNullable(response.getHeader("Content-Length"))
        .map(Integer::valueOf)
        .orElse(-1);
  }
}
