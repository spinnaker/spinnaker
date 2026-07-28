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

package com.netflix.spinnaker.gate.security.token;

import com.netflix.spinnaker.kork.common.Header;
import com.netflix.spinnaker.security.authz.filter.IdentityTokenAuthenticationConverter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Edge-side inbound verification of the signed identity token (Component 3, "verify inbound at
 * Gate"). Unlike the generic downstream {@code IdentityTokenAuthenticationFilter}, Gate is the
 * authentication edge: browser/CLI requests authenticate via session/OAuth/SAML/x509/API-token and
 * do <em>not</em> carry an identity token. This filter therefore only acts when an {@link
 * Header#IDENTITY_TOKEN} is actually present and the request is not already authenticated, so it
 * never clobbers an established edge session.
 *
 * <p>Honors {@code authz.enabled} via the wrapped {@link IdentityTokenAuthenticationConverter} (an
 * invalid token is rejected when authorization is enabled, ignored when disabled). Token
 * <em>absence</em> is always allowed at the edge — enforcement of token presence is a
 * downstream-service concern.
 */
public class GateIdentityTokenInboundFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(GateIdentityTokenInboundFilter.class);

  private final IdentityTokenAuthenticationConverter converter;

  public GateIdentityTokenInboundFilter(IdentityTokenAuthenticationConverter converter) {
    this.converter = converter;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String token = request.getHeader(Header.IDENTITY_TOKEN.getHeader());
    if (StringUtils.isNotBlank(token) && !alreadyAuthenticated()) {
      try {
        Authentication authentication = converter.convert(request);
        if (authentication != null) {
          SecurityContext context = SecurityContextHolder.createEmptyContext();
          context.setAuthentication(authentication);
          SecurityContextHolder.setContext(context);
        }
      } catch (RuntimeException e) {
        log.debug("Failed to apply inbound identity token", e);
      }
    }
    chain.doFilter(request, response);
  }

  private static boolean alreadyAuthenticated() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null
        && authentication.isAuthenticated()
        && !(authentication instanceof AnonymousAuthenticationToken);
  }
}
