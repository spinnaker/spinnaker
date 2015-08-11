/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.security.x509

import com.netflix.spinnaker.gate.security.AnonymousAccountsService
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

  private final AnonymousAccountsService anonymousAccountsService

  X509AuthenticationProvider(AnonymousAccountsService anonymousAccountsService) {
    this.anonymousAccountsService = anonymousAccountsService
  }

  @Override
  Authentication authenticate(Authentication authentication) throws AuthenticationException {
    if (!(authentication.credentials instanceof X509Certificate)) {
      return null
    }

    def x509 = (X509Certificate) authentication.credentials
    def rfc822Name = x509.getSubjectAlternativeNames().find {
      it.find { it.toString() == RFC822_NAME_ID }
    }?.get(1) ?: authentication.principal

    return new PreAuthenticatedAuthenticationToken(
      new User(rfc822Name as String, null, null, [], anonymousAccountsService.allowedAccounts),
      authentication.credentials)
  }

  @Override
  boolean supports(Class<?> authentication) {
    return authentication.isAssignableFrom(PreAuthenticatedAuthenticationToken)
  }
}
