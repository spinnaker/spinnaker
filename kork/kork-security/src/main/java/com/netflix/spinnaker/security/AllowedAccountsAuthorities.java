/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.security;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class AllowedAccountsAuthorities {
  public static final String PREFIX = "ALLOWED_ACCOUNT_";

  public static Collection<GrantedAuthority> getAllowedAccountAuthorities(UserDetails userDetails) {
    if (userDetails == null
        || userDetails.getAuthorities() == null
        || userDetails.getAuthorities().isEmpty()) {
      return Collections.emptySet();
    }

    return userDetails.getAuthorities().stream()
        .filter(a -> a.getAuthority().startsWith(PREFIX))
        .collect(Collectors.toSet());
  }

  @SuppressWarnings("deprecation")
  public static Collection<String> getAllowedAccounts(UserDetails userDetails) {
    if (userDetails instanceof User) {
      return ((User) userDetails).getAllowedAccounts();
    }
    return getAllowedAccountAuthorities(userDetails).stream()
        .map(a -> a.getAuthority().substring(PREFIX.length()))
        .sorted()
        .collect(Collectors.toList());
  }

  public static Collection<GrantedAuthority> buildAllowedAccounts(Collection<String> accounts) {
    if (accounts == null || accounts.isEmpty()) {
      return Collections.emptySet();
    }

    return accounts.stream()
        .filter(Objects::nonNull)
        .filter(s -> !s.isEmpty())
        .map(String::toLowerCase)
        .map(s -> PREFIX + s)
        .map(SimpleGrantedAuthority::new)
        .collect(Collectors.toSet());
  }
}
