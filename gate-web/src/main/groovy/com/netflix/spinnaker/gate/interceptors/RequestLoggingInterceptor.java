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
package com.netflix.spinnaker.gate.interceptors;

import static net.logstash.logback.argument.StructuredArguments.value;

import java.util.concurrent.TimeUnit;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;
import org.springframework.web.util.ContentCachingResponseWrapper;

public class RequestLoggingInterceptor extends HandlerInterceptorAdapter {
  static final String TIMER_ATTRIBUTE = "Request_startTime";

  private Logger log = LoggerFactory.getLogger(getClass());

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws Exception {
    request.setAttribute(TIMER_ATTRIBUTE, System.nanoTime());
    return true;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    // 127.0.0.1 "GET /limecat.jpg HTTP/1.0" 200 2326
    try {
      MDC.put("requestDuration", getRequestDuration(request));
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
    }
  }

  private static String getRequestDuration(HttpServletRequest request) {
    Long startTime = (Long) request.getAttribute(TIMER_ATTRIBUTE);
    if (startTime == null) {
      return "n/a";
    }

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
    if (response instanceof ContentCachingResponseWrapper) {
      return ((ContentCachingResponseWrapper) response).getContentAsByteArray().length;
    }
    return -1;
  }
}
