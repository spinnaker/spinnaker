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

import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.fiat.shared.FiatService;
import com.netflix.spinnaker.fiat.shared.FiatStatus;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import io.micrometer.core.annotation.Counted;
import java.util.Collection;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Facade for logging in an authenticated user and obtaining Fiat authorities. */
@Log4j2
@Service
@RequiredArgsConstructor
public class AuthenticationService {
  private final FiatStatus fiatStatus;
  private final FiatService fiatService;
  private final FiatPermissionEvaluator permissionEvaluator;

  @Setter(
      onParam_ = {@Qualifier("fiatLoginService")},
      onMethod_ = {@Autowired(required = false)})
  private FiatService fiatLoginService;

  private FiatService getFiatServiceForLogin() {
    return fiatLoginService != null ? fiatLoginService : fiatService;
  }

  @Counted("fiat.login")
  public Collection<? extends GrantedAuthority> login(String userid) {
    if (!fiatStatus.isEnabled()) {
      return Set.of();
    }

    return AuthenticatedRequest.allowAnonymous(
        () -> {
          Retrofit2SyncCall.execute(getFiatServiceForLogin().loginUser(userid));
          return resolveAuthorities(userid);
        });
  }

  @Counted("fiat.login")
  public Collection<? extends GrantedAuthority> loginWithRoles(
      String userid, Collection<String> roles) {
    if (!fiatStatus.isEnabled()) {
      return Set.of();
    }

    return AuthenticatedRequest.allowAnonymous(
        () -> {
          Retrofit2SyncCall.execute(getFiatServiceForLogin().loginWithRoles(userid, roles));
          return resolveAuthorities(userid);
        });
  }

  @Counted("fiat.logout")
  public void logout(String userid) {
    if (!fiatStatus.isEnabled()) {
      return;
    }

    Retrofit2SyncCall.execute(getFiatServiceForLogin().logoutUser(userid));
    permissionEvaluator.invalidatePermission(userid);
  }

  private Collection<? extends GrantedAuthority> resolveAuthorities(String userid) {
    permissionEvaluator.invalidatePermission(userid);
    var permission = permissionEvaluator.getPermission(userid);
    if (permission == null) {
      throw new UsernameNotFoundException(
          String.format("No user found in Fiat named '%s'", userid));
    }
    return permission.toGrantedAuthorities();
  }
}
