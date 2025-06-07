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

import static java.util.stream.Collectors.toList;

import com.netflix.spinnaker.security.User;
import java.util.Collection;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedGrantedAuthoritiesUserDetailsService;

/**
 * Provide details for pre-authenticated tokens using kork-security's deprecated User class. See
 * HeaderAuthConfig.authenticationProvider for background. If gate stops using this class, perhaps
 * replacing it with spring-security-core's User (or the UserDetails interface where sufficient),
 * this class can likely disappear.
 */
public class HeaderAuthenticationUserDetailsService
    extends PreAuthenticatedGrantedAuthoritiesUserDetailsService {

  @Override
  protected UserDetails createUserDetails(
      Authentication token, Collection<? extends GrantedAuthority> authorities) {
    User user = new User();
    user.setEmail(token.getName());
    user.setRoles(authorities.stream().map(GrantedAuthority::getAuthority).collect(toList()));
    return user;
  }
}
