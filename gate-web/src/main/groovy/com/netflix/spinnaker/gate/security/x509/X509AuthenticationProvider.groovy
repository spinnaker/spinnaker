package com.netflix.spinnaker.gate.security.x509

import com.netflix.spinnaker.security.User
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken

import java.security.cert.X509Certificate

class X509AuthenticationProvider implements AuthenticationProvider {
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
  Authentication authenticate(Authentication authentication) throws AuthenticationException {
    def x509 = (X509Certificate) authentication.credentials
    def rfc822Name = x509.getSubjectAlternativeNames().find {
      it.find { it.toString() == RFC822_NAME_ID }
    }?.get(1) ?: authentication.principal

    return new PreAuthenticatedAuthenticationToken(
      new User(rfc822Name as String, null, null, [], []),
      authentication.credentials)
  }

  @Override
  boolean supports(Class<?> authentication) {
    return authentication.isAssignableFrom(PreAuthenticatedAuthenticationToken)
  }
}
