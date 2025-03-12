/*
 * Copyright 2017 Google, Inc.
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

import static net.logstash.logback.argument.StructuredArguments.value;

import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.fiat.shared.FiatStatus;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@Log4j2
@NonnullByDefault
public class FiatSessionFilter extends OncePerRequestFilter {
  private final FiatStatus fiatStatus;
  private final FiatPermissionEvaluator permissionEvaluator;

  public FiatSessionFilter(FiatStatus fiatStatus, FiatPermissionEvaluator permissionEvaluator) {
    this.fiatStatus = fiatStatus;
    this.permissionEvaluator = permissionEvaluator;
  }

  /**
   * This filter checks if the user has an entry in Fiat, and if not, forces them to re-login. This
   * is handy for (re)populating the Fiat user repo for a deployment with existing users & sessions.
   */
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    UserPermission.View fiatPermission = null;

    if (fiatStatus.isEnabled()) {
      final String user = AuthenticatedRequest.getSpinnakerUser().orElse(null);
      log.debug("Fiat session filter - found user: {}", user);

      if (user != null) {
        fiatPermission = permissionEvaluator.getPermission(user);
        if (fiatPermission == null) {
          HttpSession session = request.getSession(false);
          if (session != null) {
            log.info(
                "Invalidating user '{}' session '{}' because Fiat permission was not found.",
                value("user", user),
                value("session", session));
            session.invalidate();
            SecurityContextHolder.clearContext();
          }
        }
      } else {
        log.warn(
            "Authenticated user was not present in authenticated request. Check authentication settings.");
      }

    } else {
      log.debug(
          "Skipping Fiat session filter: Both `services.fiat.enabled` ({}) and the FiatSessionFilter need to be enabled.",
          fiatStatus.isEnabled());
    }

    try {
      chain.doFilter(request, response);
    } finally {
      if (fiatPermission != null && fiatPermission.isLegacyFallback()) {
        log.info("Invalidating fallback permissions for {}", fiatPermission.getName());
        permissionEvaluator.invalidatePermission(fiatPermission.getName());
      }
    }
  }
}
