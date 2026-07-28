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

package com.netflix.spinnaker.security.authz.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationConverter;

/**
 * Per-request filter that establishes the Spring {@link SecurityContext} from the verified identity
 * token (via {@link IdentityTokenAuthenticationConverter}). Instead of trusting unsigned headers,
 * downstream services populate authorities from a cryptographically verified token.
 */
public class IdentityTokenAuthenticationFilter extends HttpFilter {

  private static final Logger log =
      LoggerFactory.getLogger(IdentityTokenAuthenticationFilter.class);

  private final AuthenticationConverter authenticationConverter;

  public IdentityTokenAuthenticationFilter(AuthenticationConverter authenticationConverter) {
    this.authenticationConverter = authenticationConverter;
  }

  @Override
  protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws IOException, ServletException {
    Authentication auth = authenticationConverter.convert(req);
    if (auth != null) {
      SecurityContext ctx = SecurityContextHolder.createEmptyContext();
      ctx.setAuthentication(auth);
      SecurityContextHolder.setContext(ctx);
      if (log.isDebugEnabled()) {
        log.debug("Set SecurityContext to user: {}", auth.getPrincipal());
      }
    }
    chain.doFilter(req, res);
  }
}
