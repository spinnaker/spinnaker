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

import com.netflix.spinnaker.fiat.shared.FiatClientConfigurationProperties
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
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

@Slf4j
class FiatSessionFilter implements Filter {

  FiatClientConfigurationProperties configProps

  FiatPermissionEvaluator permissionEvaluator

  FiatSessionFilter(FiatClientConfigurationProperties configProps,
                    FiatPermissionEvaluator permissionEvaluator) {
    this.configProps = configProps
    this.permissionEvaluator = permissionEvaluator
  }

  /**
   * This filter checks if the user has an entry in Fiat, and if not, forces them to re-login. This
   * is handy for (re)populating the Fiat user repo for a deployment with existing users & sessions.
   */
  @Override
  void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
    if (configProps.enabled) {
      String user = AuthenticatedRequest.getSpinnakerUser().orElse(null)
      if (permissionEvaluator.getPermission(user) == null) {
        HttpServletRequest httpReq = (HttpServletRequest) request
        log.info("Invalidating user '${user}' session '${httpReq.session.id}'" +
                     " because Fiat permission was not found.")
        httpReq.session.invalidate()
        SecurityContextHolder.clearContext()
      }
    }
    chain.doFilter(request, response)
  }

  @Override
  void init(FilterConfig filterConfig) throws ServletException {

  }

  @Override
  void destroy() {

  }
}
