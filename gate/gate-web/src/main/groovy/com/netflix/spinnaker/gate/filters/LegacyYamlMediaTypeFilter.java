/*
 * Copyright 2026 Netflix, Inc.
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

package com.netflix.spinnaker.gate.filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Backwards compatibility: Gate used to speak {@code application/x-yaml}; it now uses the standard
 * {@code application/yaml}. This rewrites the {@code Content-Type} and {@code Accept} request
 * headers of existing clients so they keep working. Delete this class once clients have moved.
 */
@Component
public class LegacyYamlMediaTypeFilter extends OncePerRequestFilter {
  private static final String LEGACY = "application/x-yaml";
  private static final String CURRENT = "application/yaml";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    chain.doFilter(needsRewrite(request) ? new Rewriting(request) : request, response);
  }

  private static boolean needsRewrite(HttpServletRequest request) {
    return isLegacy(request.getHeader("Content-Type")) || isLegacy(request.getHeader("Accept"));
  }

  private static boolean isLegacy(String value) {
    return value != null && value.toLowerCase(Locale.ROOT).contains(LEGACY);
  }

  private static String rewrite(String value) {
    return value == null ? null : value.replaceAll("(?i)" + LEGACY, CURRENT);
  }

  private static boolean isRewritten(String name) {
    return "Content-Type".equalsIgnoreCase(name) || "Accept".equalsIgnoreCase(name);
  }

  private static class Rewriting extends HttpServletRequestWrapper {
    Rewriting(HttpServletRequest request) {
      super(request);
    }

    @Override
    public String getContentType() {
      return rewrite(super.getContentType());
    }

    @Override
    public String getHeader(String name) {
      String value = super.getHeader(name);
      return isRewritten(name) ? rewrite(value) : value;
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
      Enumeration<String> values = super.getHeaders(name);
      if (!isRewritten(name)) {
        return values;
      }
      return Collections.enumeration(
          Collections.list(values).stream().map(v -> rewrite(v)).toList());
    }
  }
}
