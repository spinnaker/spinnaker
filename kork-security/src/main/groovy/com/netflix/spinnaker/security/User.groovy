/*
 * Copyright 2015 Netflix, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.netflix.spinnaker.security

import com.fasterxml.jackson.annotation.JsonIgnore
import groovy.transform.Immutable
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

/**
 * User implements UserDetails in order to properly hook into the Spring Security framework.
 */
class User implements UserDetails {
  static final long serialVersionUID = 7392392099262597885L

  String email
  String username
  String firstName
  String lastName

  Collection<String> roles = []
  Collection<String> allowedAccounts = []

  @Override
  List getAuthorities() {
    roles.collect { new SimpleGrantedAuthority(it) }
  }

  @Override
  String getUsername() {
    return this.username ?: this.email
  }

  ImmutableUser asImmutable() {
    new ImmutableUser()
  }

  /** Not used **/
  @JsonIgnore
  final String password = ""

  @Override
  boolean isAccountNonExpired() { return true }

  @Override
  boolean isAccountNonLocked() { return true }

  @Override
  boolean isCredentialsNonExpired() { return true }

  @Override
  boolean isEnabled() { return true }

  @Immutable
  class ImmutableUser extends User {
    String email = User.this.email
    String username = User.this.username
    String firstName = User.this.firstName
    String lastName = User.this.lastName

    // Use .collect() to snapshot these collections.
    Collection<String> roles = User.this.roles.collect()
    Collection<String> allowedAccounts = User.this.allowedAccounts.collect()
    List<GrantedAuthority> authorities = User.this.authorities.collect()
  }
}
