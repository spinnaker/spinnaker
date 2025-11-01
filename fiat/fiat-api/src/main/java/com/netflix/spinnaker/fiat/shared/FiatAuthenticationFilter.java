/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.fiat.shared;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationConverter;

@Slf4j
public class FiatAuthenticationFilter extends HttpFilter {

  private final FiatStatus fiatStatus;
  private final AuthenticationConverter authenticationConverter;

  public FiatAuthenticationFilter(
      FiatStatus fiatStatus, AuthenticationConverter authenticationConverter) {
    this.fiatStatus = fiatStatus;
    this.authenticationConverter = authenticationConverter;
  }

  @Override
  protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws IOException, ServletException {
    if (fiatStatus.isEnabled()) {
      SecurityContext ctx = SecurityContextHolder.createEmptyContext();
      Authentication auth = authenticationConverter.convert(req);
      ctx.setAuthentication(auth);
      SecurityContextHolder.setContext(ctx);
      log.debug("Set SecurityContext to user: {}", auth.getPrincipal().toString());
    }
    chain.doFilter(req, res);
  }
}
