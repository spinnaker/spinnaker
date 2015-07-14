/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.gate.security.saml

import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.authority.SimpleGrantedAuthority

/**
 * The SAML auth provider. By now, the user should have already been authenticated through a SAML IdP, and they've
 * sent us back a response, which we've decoded and reconstructed into a Spring Security authentication object.
 *
 * @author Dan Woods
 */
class SAMLAuthenticationProvider implements AuthenticationProvider {

  static final String YOLO_ROLE = "USER_ROLE"

  @Value('${saml.user.domain:netflix.com}')
  String userDomain

  @Override
  Authentication authenticate(Authentication authentication) throws AuthenticationException {
    String email = authentication.name

    email.endsWith("@${userDomain}") ?
        new UsernamePasswordAuthenticationToken(email, "", [new SimpleGrantedAuthority(YOLO_ROLE)])
        : null
  }

  @Override
  boolean supports(Class<?> authentication) {
    true
  }
}
