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

package com.netflix.spinnaker.filters

import com.netflix.spinnaker.kork.common.Header
import com.netflix.spinnaker.security.AllowedAccountsAuthorities
import com.netflix.spinnaker.security.AuthenticatedRequest
import groovy.util.logging.Slf4j
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.security.core.userdetails.UserDetails

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.FilterConfig
import jakarta.servlet.ServletException
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import java.security.cert.X509Certificate

@Slf4j
class AuthenticatedRequestFilter implements Filter {
  private static final String X509_CERTIFICATE = "javax.servlet.request.X509Certificate"

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
  private final boolean extractSpinnakerUserOriginHeader
  private final boolean forceNewSpinnakerRequestId
  private final boolean clearAuthenticatedRequestPostFilter

  public AuthenticatedRequestFilter(boolean extractSpinnakerHeaders = false,
                                    boolean extractSpinnakerUserOriginHeader = false,
                                    boolean forceNewSpinnakerRequestId = false,
                                    boolean clearAuthenticatedRequestPostFilter = true) {
    this.extractSpinnakerHeaders = extractSpinnakerHeaders
    this.extractSpinnakerUserOriginHeader = extractSpinnakerUserOriginHeader
    this.forceNewSpinnakerRequestId = forceNewSpinnakerRequestId
    this.clearAuthenticatedRequestPostFilter = clearAuthenticatedRequestPostFilter
  }

  @Override
  void init(FilterConfig filterConfig) throws ServletException {}

  @Override
  void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
    def spinnakerUser = null
    def spinnakerAccounts = null
    HashMap<String, String> otherSpinnakerHeaders = new HashMap<>()

    try {
      def session = ((HttpServletRequest) request).getSession(false)
      def securityContext = (SecurityContextImpl) session?.getAttribute("SPRING_SECURITY_CONTEXT")
      if (!securityContext) {
        securityContext = SecurityContextHolder.getContext()
      }

      def principal = securityContext?.authentication?.principal
      if (principal && principal instanceof UserDetails) {
        spinnakerUser = principal.username
        spinnakerAccounts = AllowedAccountsAuthorities.getAllowedAccounts(principal).join(",")
      }
    } catch (Exception e) {
      log.error("Unable to extract spinnaker user and account information", e)
    }

    if (extractSpinnakerHeaders) {
      def httpServletRequest = (HttpServletRequest) request
      spinnakerUser = spinnakerUser ?: httpServletRequest.getHeader(Header.USER.getHeader())
      spinnakerAccounts = spinnakerAccounts ?: httpServletRequest.getHeader(Header.ACCOUNTS.getHeader())

      Enumeration<String> headers = httpServletRequest.getHeaderNames()

      for (header in headers) {
        String headerUpper = header.toUpperCase()

        if (headerUpper.startsWith(Header.XSpinnakerPrefix)) {
          otherSpinnakerHeaders.put(headerUpper, httpServletRequest.getHeader(header))
        }
      }
    }

    if (extractSpinnakerUserOriginHeader) {
      otherSpinnakerHeaders.put(
        Header.USER_ORIGIN.getHeader(),
        "deck".equalsIgnoreCase(((HttpServletRequest) request).getHeader("X-RateLimit-App")) ? "deck" : "api"
      )
    }

    if (forceNewSpinnakerRequestId) {
      otherSpinnakerHeaders.put(
        Header.REQUEST_ID.getHeader(),
        UUID.randomUUID().toString()
      )
    }

    // only extract from the x509 certificate if `spinnakerUser` has not been supplied as a header
    if (request.isSecure() && !spinnakerUser) {
      ((X509Certificate[]) request.getAttribute(X509_CERTIFICATE))?.each {
        def emailSubjectName = it.getSubjectAlternativeNames().find {
          it.find { it.toString() == RFC822_NAME_ID }
        }?.get(1)

        spinnakerUser = emailSubjectName
      }
    }

    try {
      if (spinnakerUser) {
        AuthenticatedRequest.setUser(spinnakerUser)
      }

      if (spinnakerAccounts) {
        AuthenticatedRequest.setAccounts(spinnakerAccounts)
      }

      for (header in otherSpinnakerHeaders) {
        AuthenticatedRequest.set(header.key, header.value)
      }

      chain.doFilter(request, response)
    } finally {
      if (clearAuthenticatedRequestPostFilter) {
        AuthenticatedRequest.clear()
      }
    }
  }

  @Override
  void destroy() {}
}
