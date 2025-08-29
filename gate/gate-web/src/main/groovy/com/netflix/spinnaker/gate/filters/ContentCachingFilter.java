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

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * This filter simply buffers/caches the response so that the Content-Length header can be set.
 * Setting the Content-Length header prevents a response from being transferred with chunked
 * encoding which may be problematic for some http clients.
 */
public class ContentCachingFilter implements Filter {
  @Override
  public void init(FilterConfig filterConfig) {}

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {

    ContentCachingResponseWrapper responseWrapper =
        new ContentCachingResponseWrapper((HttpServletResponse) response);

    chain.doFilter(request, responseWrapper);
    responseWrapper.copyBodyToResponse();
  }

  @Override
  public void destroy() {}
}
