/*
 * Copyright 2015 Netflix, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.netflix.spinnaker.security


import groovy.util.logging.Slf4j
import org.slf4j.MDC
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl

import javax.servlet.http.HttpServletRequest
import java.security.cert.X509Certificate
import javax.servlet.FilterChain
import javax.servlet.FilterConfig
import javax.servlet.ServletException
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse

@Slf4j
class AuthenticatedRequest {
  private static final String X509_CERTIFICATE = "javax.servlet.request.X509Certificate"
  private static final String SPINNAKER_USER = "X-SPINNAKER-USER"
  private static final String SPINNAKER_ACCOUNTS = "X-SPINNAKER-ACCOUNTS"

  static class Filter implements javax.servlet.Filter {
    /*
      otherName                       [0]
      rfc822Name                      [1]
      dNSName                         [2]
      x400Address                     [3]
      directoryName                   [4]
      ediPartyName                    [5]
      uniformResourceIdentifier       [6]
      iPAddress                       [7]
      registeredID                    [8]
    */
    private static final String RFC822_NAME_ID = "1"

    private final boolean extractSpinnakerHeaders

    public Filter(boolean extractSpinnakerHeaders = false) {
      this.extractSpinnakerHeaders = extractSpinnakerHeaders
    }

    @Override
    void init(FilterConfig filterConfig) throws ServletException {}

    @Override
    void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
      def spinnakerUser = null

      if (request.isSecure()) {
        ((X509Certificate[]) request.getAttribute(X509_CERTIFICATE))?.each {
          def emailSubjectName = it.getSubjectAlternativeNames().find {
            it.find { it.toString() == RFC822_NAME_ID }
          }?.get(1)

          spinnakerUser = spinnakerUser ?: emailSubjectName
        }
      }

      if (!spinnakerUser) {
        def session = ((HttpServletRequest) request).getSession(false)
        def securityContext = (SecurityContextImpl) session?.getAttribute("SPRING_SECURITY_CONTEXT")
        def principal = securityContext?.authentication?.principal
        if (principal && principal instanceof User) {
          spinnakerUser = principal.email
        }
      }

      def spinnakerAccounts = null
      if (extractSpinnakerHeaders) {
        def httpServletRequest = (HttpServletRequest) request
        spinnakerUser = spinnakerUser ?: httpServletRequest.getHeader(SPINNAKER_USER)
        spinnakerAccounts = httpServletRequest.getHeader(SPINNAKER_ACCOUNTS)
      }

      try {
        if (spinnakerUser) {
          MDC.put(SPINNAKER_USER, spinnakerUser)
        }
        if (spinnakerAccounts) {
          MDC.put(SPINNAKER_ACCOUNTS, spinnakerAccounts)
        }

        chain.doFilter(request, response)
      } finally {
        MDC.remove(SPINNAKER_USER)
        MDC.remove(SPINNAKER_ACCOUNTS)
      }
    }

    @Override
    void destroy() {}
  }

  /**
   * Ensure an appropriate MDC context is available when {@code closure} is executed.
   */
  public static final Closure propagate(Closure closure,
                                        Object principal = SecurityContextHolder.context?.authentication?.principal) {
    def spinnakerUser = getSpinnakerUser(principal).orElse(null)
    if (!spinnakerUser) {
      return closure
    }

    def spinnakerAccounts = getSpinnakerAccounts(principal).orElse(null)

    return {
      def originalSpinnakerUser = MDC.get(SPINNAKER_USER)
      def originalSpinnakerAccounts = MDC.get(SPINNAKER_ACCOUNTS)
      try {
        MDC.put(SPINNAKER_USER, spinnakerUser)
        MDC.put(SPINNAKER_ACCOUNTS, spinnakerAccounts)
        closure()
      } finally {
        if (originalSpinnakerUser) {
          MDC.put(SPINNAKER_USER, originalSpinnakerUser)
        } else {
          MDC.remove(SPINNAKER_USER)
        }

        if (originalSpinnakerAccounts) {
          MDC.put(SPINNAKER_ACCOUNTS, originalSpinnakerAccounts)
        } else {
          MDC.remove(SPINNAKER_ACCOUNTS)
        }
      }
    }
  }

  public static Map<String, Optional<String>> getAuthenticationHeaders() {
    return [
      (SPINNAKER_USER)    : getSpinnakerUser(),
      (SPINNAKER_ACCOUNTS): getSpinnakerAccounts()
    ]
  }

  public static Optional<String> getSpinnakerUser(
    Object principal = SecurityContextHolder.context?.authentication?.principal
  ) {
    def spinnakerUser = MDC.get(SPINNAKER_USER)

    if (principal && principal instanceof User) {
      spinnakerUser = principal.email
    }

    return Optional.ofNullable(spinnakerUser)
  }

  public static Optional<String> getSpinnakerAccounts(
    Object principal = SecurityContextHolder.context?.authentication?.principal
  ) {
    def spinnakerAccounts = MDC.get(SPINNAKER_ACCOUNTS)

    if (principal && principal instanceof User && principal.allowedAccounts) {
      spinnakerAccounts = principal.allowedAccounts.join(",")
    }

    return Optional.ofNullable(spinnakerAccounts)
  }
}
