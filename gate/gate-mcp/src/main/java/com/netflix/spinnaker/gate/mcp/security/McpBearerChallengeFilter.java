/*
 * Copyright 2026 Spinnaker.io
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

package com.netflix.spinnaker.gate.mcp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Sits directly after {@code ExceptionTranslationFilter} in each security chain and, for requests
 * to the MCP endpoint only, turns an authorization failure of an unauthenticated caller into the
 * {@link McpBearerChallengeEntryPoint} challenge before {@code ExceptionTranslationFilter} can hand
 * it to the chain's browser entry point.
 *
 * <p>A filter rather than an {@code ExceptionHandlingConfigurer#defaultAuthenticationEntryPointFor}
 * mapping: the first mapping registered on a chain also becomes that chain's fallback entry point,
 * so registering the MCP mapping ahead of the SSO login mapping would change the response for every
 * unmatched non-MCP request (e.g. Deck's XHR calls) as well. Authenticated callers' failures, and
 * every request outside the MCP endpoint, are left untouched.
 */
public class McpBearerChallengeFilter extends OncePerRequestFilter {

  private final RequestMatcher mcpEndpoint;
  private final AuthenticationEntryPoint challenge;
  private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

  public McpBearerChallengeFilter(RequestMatcher mcpEndpoint, AuthenticationEntryPoint challenge) {
    this.mcpEndpoint = mcpEndpoint;
    this.challenge = challenge;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !mcpEndpoint.matches(request);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      filterChain.doFilter(request, response);
    } catch (AuthenticationException e) {
      if (response.isCommitted()) {
        throw e;
      }
      challenge.commence(request, response, e);
    } catch (AccessDeniedException e) {
      if (response.isCommitted() || isAuthenticated()) {
        throw e;
      }
      challenge.commence(request, response, null);
    }
  }

  private boolean isAuthenticated() {
    return trustResolver.isAuthenticated(SecurityContextHolder.getContext().getAuthentication());
  }
}
