package com.netflix.spinnaker.gate.filters

import groovy.util.logging.Slf4j
import org.slf4j.MDC
import org.springframework.security.core.context.SecurityContextImpl

import javax.servlet.http.HttpServletRequest
import java.security.cert.X509Certificate
import javax.servlet.Filter
import javax.servlet.FilterChain
import javax.servlet.FilterConfig
import javax.servlet.ServletException
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse

@Slf4j
class AuthenticatedRequestLoggingFilter implements Filter {
  private static final String X509_CERTIFICATE = "javax.servlet.request.X509Certificate"
  private static final String AUTHENTICATED_USER = "AUTHENTICATED_USER"

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

  @Override
  void init(FilterConfig filterConfig) throws ServletException {}

  @Override
  void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
    def authenticatedUser = null

    if (request.isSecure()) {
      ((X509Certificate[]) request.getAttribute(X509_CERTIFICATE))?.each {
        def emailSubjectName = it.getSubjectAlternativeNames().find {
          it.find { it.toString() == RFC822_NAME_ID }
        }?.get(1)

        authenticatedUser = authenticatedUser ?: emailSubjectName
      }
    }

    if (!authenticatedUser) {
      def session = ((HttpServletRequest) request).getSession(false)
      def securityContext = (SecurityContextImpl) session?.getAttribute("SPRING_SECURITY_CONTEXT")
      authenticatedUser = securityContext?.authentication?.principal?.email
    }

    try {
      if (authenticatedUser) {
        MDC.put(AUTHENTICATED_USER, authenticatedUser)
      }

      chain.doFilter(request, response)
    } finally {
      MDC.remove(AUTHENTICATED_USER)
    }
  }

  @Override
  void destroy() {}

  /**
   * Ensure an appropriate MDC context is available when {@code closure} is executed.
   */
  public static final Closure applyMDC(Closure closure) {
    def authenticatedUser = MDC.get(AUTHENTICATED_USER)
    if (!authenticatedUser) {
      return closure
    }

    return {
      try {
        MDC.put(AUTHENTICATED_USER, authenticatedUser)
        closure()
      } finally {
        MDC.remove(AUTHENTICATED_USER)
      }
    }
  }
}
