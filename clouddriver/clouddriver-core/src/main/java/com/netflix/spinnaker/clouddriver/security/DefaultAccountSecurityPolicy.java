/*
 * Copyright 2022 Apple Inc.
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

package com.netflix.spinnaker.clouddriver.security;

import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.security.SpinnakerAuthorities;
import com.netflix.spinnaker.security.authz.Authorization;
import com.netflix.spinnaker.security.authz.PolicyDecisionPointPermissionEvaluator;
import com.netflix.spinnaker.security.authz.ResourceType;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Owner-local {@link AccountSecurityPolicy}. Decides account authorization from the caller's
 * verified identity token (the {@code SecurityContext} authorities populated by the kork-authz
 * identity-token filter) plus Clouddriver's own in-process account ACLs — no remote {@code
 * getUserPermission} call.
 *
 * <p>The {@code username} arguments are retained to satisfy the {@link AccountSecurityPolicy}
 * contract (the {@code @PreAuthorize} call sites pass {@code authentication.name}); the decision is
 * made against the current request's authentication, which is always the same principal.
 */
@RequiredArgsConstructor
@NonnullByDefault
public class DefaultAccountSecurityPolicy implements AccountSecurityPolicy {
  private final PolicyDecisionPointPermissionEvaluator permissionEvaluator;

  @Override
  public boolean isAdmin(String username) {
    return SpinnakerAuthorities.isAdmin(authentication());
  }

  @Override
  public boolean isAccountManager(String username) {
    Authentication auth = authentication();
    return SpinnakerAuthorities.isAdmin(auth) || SpinnakerAuthorities.isAccountManager(auth);
  }

  @Override
  public Set<String> getRoles(String username) {
    return new HashSet<>(SpinnakerAuthorities.getRoles(authentication()));
  }

  @Override
  public boolean canUseAccount(@Nonnull String username, @Nonnull String account) {
    // WRITE is required to do anything with an account; READ only gates certain UI items. Admins
    // (and account managers, via the evaluator's account short-circuit) bypass the ACL.
    Authentication auth = authentication();
    return SpinnakerAuthorities.isAdmin(auth)
        || permissionEvaluator.hasPermission(
            auth, account, ResourceType.ACCOUNT.getName(), Authorization.WRITE);
  }

  @Override
  public boolean canModifyAccount(@Nonnull String username, @Nonnull String account) {
    Authentication auth = authentication();
    return SpinnakerAuthorities.isAdmin(auth)
        || (SpinnakerAuthorities.isAccountManager(auth)
            && permissionEvaluator.hasPermission(
                auth, account, ResourceType.ACCOUNT.getName(), Authorization.WRITE));
  }

  private static Authentication authentication() {
    return SecurityContextHolder.getContext().getAuthentication();
  }
}
