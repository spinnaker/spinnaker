/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.gate.filters

import com.netflix.spinnaker.fiat.model.UserPermission
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
import com.netflix.spinnaker.fiat.shared.FiatStatus
import com.netflix.spinnaker.security.AuthenticatedRequest
import groovy.util.logging.Slf4j
import org.springframework.security.core.context.SecurityContextHolder

import javax.servlet.Filter
import javax.servlet.FilterChain
import javax.servlet.FilterConfig
import javax.servlet.ServletException
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpSession

import static net.logstash.logback.argument.StructuredArguments.value

@Slf4j
class FiatSessionFilter implements Filter {

  boolean enabled

  FiatStatus fiatStatus

  FiatPermissionEvaluator permissionEvaluator

  FiatSessionFilter(boolean enabled,
                    FiatStatus fiatStatus,
                    FiatPermissionEvaluator permissionEvaluator) {
    this.enabled = enabled
    this.fiatStatus = fiatStatus
    this.permissionEvaluator = permissionEvaluator
  }

  /**
   * This filter checks if the user has an entry in Fiat, and if not, forces them to re-login. This
   * is handy for (re)populating the Fiat user repo for a deployment with existing users & sessions.
   */
  @Override
  void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
    UserPermission.View fiatPermission = null

    if (fiatStatus.isEnabled() && this.enabled) {
      String user = AuthenticatedRequest.getSpinnakerUser().orElse(null)
      log.debug("Fiat session filter - found user: ${user}")

      if (user != null) {
        fiatPermission = permissionEvaluator.getPermission(user)
        if (fiatPermission == null) {
          HttpServletRequest httpReq = (HttpServletRequest) request
          HttpSession session = httpReq.getSession(false)
          if (session != null) {
            log.info("Invalidating user '{}' session '{}' because Fiat permission was not found.",
                value("user", user),
                value("session", session))
            session.invalidate()
            SecurityContextHolder.clearContext()
          }
        }
      } else {
        log.warn("Authenticated user was not present in authenticated request. Check authentication settings.")
      }
    } else {
      if (log.isDebugEnabled()) {
        log.debug("Skipping Fiat session filter: Both `services.fiat.enabled` " +
                      "(${fiatStatus.isEnabled()}) and the FiatSessionFilter (${this.enabled}) " +
                      "need to be enabled.")
      }
    }

    try {
      chain.doFilter(request, response)
    } finally {
      if (fiatPermission != null && fiatPermission.isLegacyFallback()) {
        log.info("Invalidating fallback permissions for ${fiatPermission.name}")
        permissionEvaluator.invalidatePermission(fiatPermission.name)
      }
    }
  }

  @Override
  void init(FilterConfig filterConfig) throws ServletException {
  }

  @Override
  void destroy() {
  }
}

