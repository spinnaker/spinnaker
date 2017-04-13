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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class RequestLoggingInterceptor extends HandlerInterceptorAdapter {

  private Logger log = LoggerFactory.getLogger(getClass());

  @Override
  public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
    // 127.0.0.1 "GET /limecat.jpg HTTP/1.0" 200 2326
    log.debug(String.format(
      "%s \"%s %s %s\" %d %d",
      sourceIpAddress(request),
      request.getMethod(),
      getRequestEndpoint(request),
      request.getProtocol(),
      response.getStatus(),
      getResponseSize(response)
    ));
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
