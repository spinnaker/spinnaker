/*
 * Copyright 2023 Apple, Inc.
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
 *
 */

package com.netflix.spinnaker.gate.services;

import com.netflix.spinnaker.gate.security.token.GateIdentityService;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import com.netflix.spinnaker.security.SpinnakerAuthorities;
import io.micrometer.core.annotation.Counted;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

/**
 * Facade for logging in an authenticated user and obtaining their granted authorities. Roles are
 * resolved locally via {@code kork-roles} and translated to Spring authorities.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class AuthenticationService {

  @Autowired(required = false)
  @Setter
  private GateIdentityService identityService;

  @Counted("authz.login")
  public Collection<? extends GrantedAuthority> login(String userid) {
    if (identityService == null) {
      return Set.of();
    }
    return AuthenticatedRequest.allowAnonymous(
        () -> toAuthorities(identityService.resolveAndCacheRoles(userid, List.of())));
  }

  @Counted("authz.login")
  public Collection<? extends GrantedAuthority> loginWithRoles(
      String userid, Collection<String> roles) {
    if (identityService == null) {
      return Set.of();
    }
    return AuthenticatedRequest.allowAnonymous(
        () -> toAuthorities(identityService.resolveAndCacheRoles(userid, roles)));
  }

  @Counted("authz.logout")
  public void logout(String userid) {
    if (identityService != null) {
      identityService.invalidate(userid);
    }
  }

  /** Translate resolved role names into Spring authorities, adding admin/account-manager flags. */
  private Collection<? extends GrantedAuthority> toAuthorities(Set<String> roles) {
    List<GrantedAuthority> authorities = new ArrayList<>();
    for (String role : roles) {
      authorities.add(SpinnakerAuthorities.forRoleName(role));
    }
    if (identityService.isAdmin(roles)) {
      authorities.add(SpinnakerAuthorities.ADMIN_AUTHORITY);
    }
    if (identityService.isAccountManager(roles)) {
      authorities.add(SpinnakerAuthorities.ACCOUNT_MANAGER_AUTHORITY);
    }
    return authorities;
  }
}
