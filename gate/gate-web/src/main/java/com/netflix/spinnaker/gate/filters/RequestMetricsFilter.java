/*
 * Copyright 2026 DoorDash, Inc.
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

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Emits a single Spectator counter per inbound HTTP request, tagged with the auth mechanism,
 * principal kind, HTTP method, and response status. Resolves auth via {@link AuthTypeResolver}
 * inside a try/finally so the tags reflect the fully populated {@code SecurityContext}.
 *
 * <pre>
 *   name: gate.requests
 *   tags:
 *     authType:      api_token | session | oauth2 | x509 | pre_authenticated | anonymous | none
 *     principalKind: user | service_account | unknown
 *     method:        GET | POST | PUT | DELETE | …
 *     statusCode:    200 | 401 | 503 | …
 *     status:        2xx | 4xx | 5xx | …
 * </pre>
 *
 * <p>High-cardinality data (principal id, URI, query) is deliberately not tagged.
 */
public class RequestMetricsFilter extends OncePerRequestFilter {

  static final String METRIC_NAME = "gate.requests";

  private final Registry registry;

  public RequestMetricsFilter(Registry registry) {
    this.registry = registry;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    try {
      chain.doFilter(request, response);
    } finally {
      int statusCode = response.getStatus();
      Id id =
          registry
              .createId(METRIC_NAME)
              .withTag("authType", AuthTypeResolver.resolveAuthType(request))
              .withTag("principalKind", AuthTypeResolver.resolvePrincipalKind(request))
              .withTag("method", request.getMethod())
              .withTag("statusCode", Integer.toString(statusCode))
              .withTag("status", statusClass(statusCode));
      registry.counter(id).increment();
    }
  }

  private static String statusClass(int statusCode) {
    if (statusCode < 100 || statusCode >= 600) {
      return "unknown";
    }
    return (statusCode / 100) + "xx";
  }
}
