/*
 * Copyright 2025 Salesforce, Inc.
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

package com.netflix.spinnaker.gate.security.header;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Slf4j
public class HeaderAuthRequestMatcher implements RequestMatcher {
  /**
   * The list of paths that RequestHeaderAuthenticationFilter doesn't process. It does seem a little
   * strange to duplicate some information that AuthConfig.configure sets up, but so be it I
   * suppose.
   */
  private final List<String> noAuthenticationRequiredPaths = List.of("/error", "/auth/logout");

  /**
   * Return true if the request requires authentication by the RequestHeaderAuthenticationFilter.
   * Return false for paths in noAuthenticationRequiredPaths, or if the request has already been
   * authenticated.
   */
  @Override
  public boolean matches(HttpServletRequest request) {
    Authentication currentUser = SecurityContextHolder.getContext().getAuthentication();
    if (currentUser != null) {
      // Already authenticated...no need to repeat.
      return false;
    }

    String requestUri = request.getRequestURI();
    boolean retval = !noAuthenticationRequiredPaths.contains(requestUri);
    log.debug("HeaderAuthRequestMatcher.matches: {} returns {}", requestUri, retval);
    return retval;
  }
}
