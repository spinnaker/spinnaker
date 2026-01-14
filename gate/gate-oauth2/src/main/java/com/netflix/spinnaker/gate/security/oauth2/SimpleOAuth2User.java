/*
 * Copyright 2026 Wise, PLC.
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

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * A simple {@link OAuth2User} implementation that wraps a map of user attributes.
 *
 * <p>This class is used as an intermediate representation when processing externally provided
 * access tokens before converting to a {@link SpinnakerOAuth2User}.
 */
public class SimpleOAuth2User implements OAuth2User {

  private final Map<String, Object> attributes;

  public SimpleOAuth2User(Map<String, Object> attributes) {
    this.attributes = attributes != null ? attributes : Collections.emptyMap();
  }

  @Override
  public Map<String, Object> getAttributes() {
    return attributes;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return Collections.emptyList();
  }

  @Override
  public String getName() {
    Object name = attributes.get("name");
    if (name != null) {
      return name.toString();
    }
    Object login = attributes.get("login");
    if (login != null) {
      return login.toString();
    }
    Object email = attributes.get("email");
    if (email != null) {
      return email.toString();
    }
    return null;
  }
}
