/*
 * Copyright 2020 OpsMx, Inc.
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

package com.netflix.spinnaker.orca.echo.util;

import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.netflix.spinnaker.kork.common.Header;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import com.netflix.spinnaker.security.token.AuthorizationProperties;
import com.netflix.spinnaker.security.token.SpinnakerTokenClaims;
import com.netflix.spinnaker.security.token.SpinnakerTokenVerifier;
import com.netflix.spinnaker.security.token.TokenValidationException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Authorizes a manual judgment by intersecting the stage's required judgment roles with the judging
 * user's roles.
 *
 * <p>This is a role-only decision (no resource ACL), so in the owner-local / token-carried model it
 * stays a purely local computation: the user's roles come from the cryptographically verified
 * identity token carried on the request ({@link Header#IDENTITY_TOKEN}) rather than from a remote
 * {@code getPermission} lookup. An administrator is always authorized.
 *
 * <p>Authorization disabled / permissive: when no verified identity token is available (no token,
 * no verifier wired, or {@code authz.enabled} still off), the judgment is allowed instead of
 * failing closed, mirroring the previous "authorization not enabled" short-circuit. This permissive
 * default can be flipped to fail-closed by setting {@code authz.strict=true} (with {@code
 * authz.enabled=true}): the judgment is then denied whenever no verified token is available rather
 * than allowed.
 */
@Component
public class ManualJudgmentAuthorization {
  private final Logger log = LoggerFactory.getLogger(getClass());

  private final SpinnakerTokenVerifier tokenVerifier;
  private final AuthorizationProperties authorizationProperties;

  @Autowired
  public ManualJudgmentAuthorization(
      Optional<SpinnakerTokenVerifier> tokenVerifier,
      Optional<AuthorizationProperties> authorizationProperties) {
    this.tokenVerifier = tokenVerifier.orElse(null);
    this.authorizationProperties = authorizationProperties.orElseGet(AuthorizationProperties::new);
  }

  /**
   * A manual judgment will be considered "authorized" if the current user has at least one of the
   * required judgment roles (or the current user is an admin).
   *
   * @param requiredJudgmentRoles Required judgment roles
   * @param currentUser User that has attempted this judgment
   * @return whether or not {@param currentUser} has authorization to judge
   */
  public boolean isAuthorized(Collection<String> requiredJudgmentRoles, String currentUser) {
    if (requiredJudgmentRoles == null || requiredJudgmentRoles.isEmpty()) {
      return true;
    }

    if (Strings.isNullOrEmpty(currentUser)) {
      return false;
    }

    Optional<SpinnakerTokenClaims> claims = resolveVerifiedClaims();
    if (claims.isEmpty()) {
      if (authorizationProperties.isEnabled() && authorizationProperties.isStrict()) {
        // Fail closed: the operator has opted into strict authorization but there is no
        // cryptographically verified token to evaluate the required judgment roles against.
        log.warn(
            "Denying manual judgment for '{}': no verified identity token was present and authz.strict is enabled",
            currentUser);
        return false;
      }
      // Permissive: no cryptographically verified roles to evaluate; don't fail closed during
      // rollout (the previous behavior when authorization was disabled was to authorize).
      log.debug(
          "No verified identity token available for '{}'; authorizing manual judgment (permissive)",
          currentUser);
      return true;
    }

    SpinnakerTokenClaims tokenClaims = claims.get();
    return tokenClaims.isAdmin() || isAuthorized(requiredJudgmentRoles, tokenClaims.getRoles());
  }

  private boolean isAuthorized(
      Collection<String> requiredJudgmentRoles, Collection<String> currentUserRoles) {
    if (requiredJudgmentRoles == null || requiredJudgmentRoles.isEmpty()) {
      return true;
    }

    if (currentUserRoles == null) {
      currentUserRoles = new ArrayList<>();
    }

    return !Sets.intersection(new HashSet<>(requiredJudgmentRoles), new HashSet<>(currentUserRoles))
        .isEmpty();
  }

  private Optional<SpinnakerTokenClaims> resolveVerifiedClaims() {
    if (tokenVerifier == null) {
      return Optional.empty();
    }
    String token = AuthenticatedRequest.get(Header.IDENTITY_TOKEN).orElse(null);
    if (StringUtils.isBlank(token)) {
      return Optional.empty();
    }
    try {
      return Optional.of(tokenVerifier.verify(token));
    } catch (TokenValidationException e) {
      log.warn("Ignoring invalid identity token during manual judgment authorization", e);
      return Optional.empty();
    }
  }
}
