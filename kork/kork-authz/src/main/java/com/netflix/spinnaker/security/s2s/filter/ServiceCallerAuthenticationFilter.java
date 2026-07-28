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

package com.netflix.spinnaker.security.s2s.filter;

import com.netflix.spinnaker.security.s2s.ServiceCaller;
import com.netflix.spinnaker.security.s2s.ServiceCallerContext;
import com.netflix.spinnaker.security.s2s.ServiceCallerResolver;
import com.netflix.spinnaker.security.s2s.config.ServiceToServiceProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mesh-wide per-request filter that authenticates the immediate service caller (via the configured
 * {@link ServiceCallerResolver}) and publishes it on {@link ServiceCallerContext} for the duration
 * of the request, so authorization can consult it without re-parsing the transport identity.
 *
 * <p>Authentication only — it does not authorize or reject. Per-endpoint authorization is codified
 * via {@link com.netflix.spinnaker.security.s2s.AllowServiceCallers} and enforced by {@link
 * com.netflix.spinnaker.security.s2s.ServiceCallerEnforcementAspect}. When service-to-service auth
 * is disabled the filter is a pass-through.
 */
public class ServiceCallerAuthenticationFilter extends HttpFilter {

  private static final Logger log =
      LoggerFactory.getLogger(ServiceCallerAuthenticationFilter.class);

  private final ServiceCallerResolver resolver;
  private final ServiceToServiceProperties properties;

  public ServiceCallerAuthenticationFilter(
      ServiceCallerResolver resolver, ServiceToServiceProperties properties) {
    this.resolver = resolver;
    this.properties = properties;
  }

  @Override
  protected void doFilter(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (!properties.isEnabled()) {
      chain.doFilter(request, response);
      return;
    }
    Optional<ServiceCaller> caller = resolveQuietly(request);
    try {
      caller.ifPresent(
          c -> {
            ServiceCallerContext.set(c);
            request.setAttribute(ServiceCallerContext.REQUEST_ATTRIBUTE, c);
            if (log.isDebugEnabled()) {
              log.debug("Authenticated service caller {} via {}", c.service(), c.source());
            }
          });
      chain.doFilter(request, response);
    } finally {
      ServiceCallerContext.clear();
    }
  }

  private Optional<ServiceCaller> resolveQuietly(HttpServletRequest request) {
    try {
      return resolver.resolve(request);
    } catch (RuntimeException e) {
      log.debug("Service caller resolution failed: {}", e.getMessage());
      return Optional.empty();
    }
  }
}
