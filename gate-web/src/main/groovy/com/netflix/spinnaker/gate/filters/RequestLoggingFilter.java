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

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.servlet.*;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class RequestLoggingFilter extends HttpFilter {
  private Logger log = LoggerFactory.getLogger(getClass());

  @Override
  protected void doFilter(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    long startTime = System.nanoTime();
    try {
      chain.doFilter(request, response);
    } finally {
      try {
        MDC.put("requestDuration", getRequestDuration(startTime));
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
      }
    }
  }

  private static String getRequestDuration(long startTime) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime) + "ms";
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
