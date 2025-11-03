/*
 * Copyright 2025 OpsMx, Inc.
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

package com.netflix.spinnaker.gate.security.oauth2;

import com.netflix.spinnaker.security.User;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * Custom implementation of {@link OAuth2User} that integrates with Spinnaker's {@link User} model.
 * This class holds OAuth2-related user details and provides attributes such as roles and allowed
 * accounts.
 *
 * <p>It extends {@link User} from Netflix Spinnaker to include Spinnaker-specific fields.
 *
 * <p>Usage: This class is used in OAuth2 authentication flows where user details are retrieved from
 * an OAuth2 provider.
 *
 * @author rahul-chekuri
 * @see User
 */
public class SpinnakerOAuth2User extends User implements OAuth2User {
  /**
   * Attributes containing user details, retrieved from the OIDC provider. These attributes
   * typically include user profile information such as name, email, and roles.
   */
  private final Map<String, Object> attributes;

  /** Authorities assigned to the user, used for authorization in Spring Security. */
  private final List<GrantedAuthority> authorities;

  public SpinnakerOAuth2User(
      String email,
      String firstName,
      String lastName,
      Collection<String> allowedAccounts,
      List<String> roles,
      String username,
      Map<String, Object> attributes,
      Collection<? extends GrantedAuthority> authorities) {
    super(email, username, firstName, lastName, roles, allowedAccounts);
    this.attributes =
        attributes != null
            ? Collections.unmodifiableMap(new HashMap<>(attributes))
            : Collections.emptyMap();
    this.authorities = authorities != null ? List.copyOf(authorities) : Collections.emptyList();
  }

  @Override
  public Map<String, Object> getAttributes() {
    return attributes;
  }

  @Override
  public List<GrantedAuthority> getAuthorities() {
    return authorities;
  }

  @Override
  public String getName() {
    return super.getUsername();
  }
}
