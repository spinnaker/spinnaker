/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.filters;

import com.google.common.base.Joiner;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import com.netflix.spinnaker.security.User;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

@AllArgsConstructor
@Slf4j
public class AuthenticatedRequestFilter implements Filter {

  private static final String X509_CERTIFICATE = "javax.servlet.request.X509Certificate";

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
  private static final String RFC822_NAME_ID = "1";

  private final boolean extractSpinnakerHeaders;
  private final boolean extractSpinnakerUserOriginHeader;
  private final boolean forceNewSpinnakerRequestId;

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest httpRequest = ((HttpServletRequest) request);

    HashMap<String, String> otherSpinnakerHeaders = new HashMap<>();

    Principal principal = getPrincipal(httpRequest);

    if (extractSpinnakerHeaders) {
      principal.applyFromHeaders(httpRequest);

      Enumeration<String> headers = httpRequest.getHeaderNames();
      while (headers.hasMoreElements()) {
        final String header = headers.nextElement();
        final String headerUpper = header.toUpperCase();
        if (headerUpper.startsWith(AuthenticatedRequest.Header.XSpinnakerPrefix)) {
          otherSpinnakerHeaders.put(headerUpper, httpRequest.getHeader(header));
        }
      }
    }

    if (extractSpinnakerUserOriginHeader) {
      otherSpinnakerHeaders.put(
          AuthenticatedRequest.Header.USER_ORIGIN.getHeader(),
          "deck".equalsIgnoreCase(httpRequest.getHeader("X-RateLimit-App")) ? "deck" : "api");
    }

    if (forceNewSpinnakerRequestId) {
      otherSpinnakerHeaders.put(
          AuthenticatedRequest.Header.REQUEST_ID.getHeader(), UUID.randomUUID().toString());
    }

    // only extract from the x509 certificate if `spinnakerUser` has not been supplied as a header
    if (httpRequest.isSecure() && principal.username == null) {
      Arrays.asList((X509Certificate[]) httpRequest.getAttribute(X509_CERTIFICATE))
          .forEach(
              cert -> {
                Collection<List<?>> sans;
                try {
                  sans = cert.getSubjectAlternativeNames();
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }

                principal.username =
                    sans.stream()
                        .filter(
                            san -> san.stream().anyMatch(v -> RFC822_NAME_ID.equals(v.toString())))
                        .findFirst()
                        .map(it -> it.get(1))
                        .toString();
              });
    }

    try {
      if (principal.username != null) {
        MDC.put(AuthenticatedRequest.Header.USER.getHeader(), principal.username);
      }
      if (principal.accounts != null) {
        MDC.put(AuthenticatedRequest.Header.ACCOUNTS.getHeader(), principal.accounts);
      }
      otherSpinnakerHeaders.forEach(MDC::put);

      chain.doFilter(request, response);
    } finally {
      MDC.clear();
      try {
        // TODO(rz): This definitely isn't right...
        // force clear to avoid the potential for a memory leak if log4j is being used
        Class<?> log4jMDC = Class.forName("org.apache.log4j.MDC");
        log4jMDC.getMethod("clear").invoke(log4jMDC);
      } catch (Exception ignored) {
      }
    }
  }

  private Principal getPrincipal(HttpServletRequest httpRequest) {
    try {
      HttpSession session = httpRequest.getSession(false);

      SecurityContextImpl securityContext =
          (SecurityContextImpl)
              Optional.ofNullable(session)
                  .map(s -> s.getAttribute("SPRING_SECURITY_CONTEXT"))
                  .orElse(SecurityContextHolder.getContext());

      Object principal =
          Optional.ofNullable(securityContext)
              .map(SecurityContextImpl::getAuthentication)
              .map(Authentication::getPrincipal);

      User user = (User) principal;
      return new Principal(user.getUsername(), Joiner.on(",").join(user.getAllowedAccounts()));
    } catch (Exception e) {
      log.error("Unable to extract Spinnaker user and account information", e);
    }
    return new Principal(null, null);
  }

  @AllArgsConstructor
  private static class Principal {
    String username;
    String accounts;

    void applyFromHeaders(HttpServletRequest httpServletRequest) {
      if (this.username == null) {
        this.username = httpServletRequest.getHeader(AuthenticatedRequest.Header.USER.getHeader());
      }
      if (this.accounts == null) {
        this.accounts =
            httpServletRequest.getHeader(AuthenticatedRequest.Header.ACCOUNTS.getHeader());
      }
    }
  }
}
