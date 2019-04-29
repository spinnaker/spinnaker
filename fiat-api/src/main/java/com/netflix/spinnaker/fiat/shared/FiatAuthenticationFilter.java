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

import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.io.IOException;
import java.util.ArrayList;
import javax.servlet.*;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

@Slf4j
public class FiatAuthenticationFilter implements Filter {

  private final FiatStatus fiatStatus;

  public FiatAuthenticationFilter(FiatStatus fiatStatus) {
    this.fiatStatus = fiatStatus;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (!fiatStatus.isEnabled()) {
      chain.doFilter(request, response);
      return;
    }

    Authentication auth =
        AuthenticatedRequest.getSpinnakerUser()
            .map(
                username ->
                    (Authentication)
                        new PreAuthenticatedAuthenticationToken(username, null, new ArrayList<>()))
            .orElseGet(
                () ->
                    new AnonymousAuthenticationToken(
                        "anonymous",
                        "anonymous",
                        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

    val ctx = SecurityContextHolder.createEmptyContext();
    ctx.setAuthentication(auth);
    SecurityContextHolder.setContext(ctx);
    log.debug("Set SecurityContext to user: {}", auth.getPrincipal().toString());
    chain.doFilter(request, response);
  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {}

  @Override
  public void destroy() {}
}
