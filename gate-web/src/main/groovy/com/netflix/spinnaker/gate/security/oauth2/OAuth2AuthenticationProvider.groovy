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

package com.netflix.spinnaker.gate.security.oauth2

import com.netflix.spinnaker.gate.security.oauth2.IdentityResourceServerTokenServices
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.provider.authentication.OAuth2AuthenticationDetails
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken

class OAuth2AuthenticationProvider implements AuthenticationProvider {
  private final IdentityResourceServerTokenServices identityResourceServerTokenServices

  OAuth2AuthenticationProvider(IdentityResourceServerTokenServices identityResourceServerTokenServices) {
    this.identityResourceServerTokenServices = identityResourceServerTokenServices
  }

  @Override
  Authentication authenticate(Authentication authentication) throws AuthenticationException {
    if (!(authentication.details instanceof OAuth2AuthenticationDetails)) {
      return null
    }

    return identityResourceServerTokenServices.loadAuthentication(authentication.principal as String)
  }

  @Override
  boolean supports(Class<?> authentication) {
    return authentication.isAssignableFrom(PreAuthenticatedAuthenticationToken)
  }
}
