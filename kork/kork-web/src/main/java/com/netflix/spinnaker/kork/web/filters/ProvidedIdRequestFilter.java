/*
 * Copyright 2025 Salesforce, Inc.
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
package com.netflix.spinnaker.kork.web.filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

/**
 * A small subset of AuthenticatedRequestFilter that extracts ids used for tracing from http
 * requests and puts the information in the MDC. This way the info is available in log messages and
 * to build headers for downstream http requests. Because AuthenticatedRequestFilter runs late in
 * the filter chain, it happens after calls to fiat during authentication. With this, the requests
 * to fiat no longer get a random UUID for X-SPINNAKER-REQUEST-ID.
 */
@Slf4j
public class ProvidedIdRequestFilter extends HttpFilter {

  private final ProvidedIdRequestFilterConfigurationProperties
      providedIdRequestFilterConfigurationProperties;
  private final List<String> allHeaders;

  public ProvidedIdRequestFilter(
      ProvidedIdRequestFilterConfigurationProperties
          providedIdRequestFilterConfigurationProperties) {
    this.providedIdRequestFilterConfigurationProperties =
        providedIdRequestFilterConfigurationProperties;

    this.allHeaders =
        Stream.concat(
                providedIdRequestFilterConfigurationProperties.getHeaders().stream(),
                providedIdRequestFilterConfigurationProperties.getAdditionalHeaders().stream())
            .map(String::toUpperCase)
            .distinct()
            .collect(Collectors.toList());
    log.debug("including {} in the MDC", this.allHeaders);
  }

  @Override
  protected void doFilter(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    Map<String, String> contextMap = MDC.getCopyOfContextMap();
    log.debug("original MDC: {}", contextMap);

    try {
      Enumeration<String> requestHeaderNames = request.getHeaderNames();

      while (requestHeaderNames.hasMoreElements()) {
        String headerName = requestHeaderNames.nextElement();

        String headerUpper = headerName.toUpperCase();

        if (allHeaders.contains(headerUpper)) {
          String value = request.getHeader(headerName);
          log.debug("including {}:{} in the MDC", headerUpper, value);
          MDC.put(headerUpper, value);
        }
      }

      chain.doFilter(request, response);
    } finally {
      if (contextMap == null) {
        log.debug("clearing the MDC");
        MDC.clear();
      } else {
        log.debug("restoring the MDC to {}", contextMap);
        MDC.setContextMap(contextMap);
      }
    }
  }
}
