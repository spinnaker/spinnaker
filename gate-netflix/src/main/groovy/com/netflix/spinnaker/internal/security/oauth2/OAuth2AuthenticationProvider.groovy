package com.netflix.spinnaker.internal.security.oauth2

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
