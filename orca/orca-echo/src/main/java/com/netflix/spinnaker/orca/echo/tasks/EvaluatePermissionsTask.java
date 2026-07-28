/*
 * Copyright 2025 DoorDash, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.echo.tasks;

import com.netflix.spinnaker.kork.common.Header;
import com.netflix.spinnaker.orca.api.pipeline.Task;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.echo.pipeline.EvaluatePermissionsStage;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import com.netflix.spinnaker.security.token.AuthorizationProperties;
import com.netflix.spinnaker.security.token.SpinnakerTokenClaims;
import com.netflix.spinnaker.security.token.SpinnakerTokenVerifier;
import com.netflix.spinnaker.security.token.TokenValidationException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nonnull;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Decides whether the executing user is a member of one of the stage's required groups.
 *
 * <p>This is a role-only decision (no resource ACL), so in the owner-local / token-carried model it
 * is purely local: the caller's roles come from the cryptographically verified identity token
 * propagated on the request ({@link Header#IDENTITY_TOKEN}) rather than from a remote {@code
 * getUserPermission} lookup. An administrator bypasses the group check.
 *
 * <p>Authorization disabled / permissive: when no verified identity token is available (no token on
 * the request, no verifier configured, or {@code authz.enabled} still off so an unsigned legacy
 * request slips through), the task skips the evaluation and lets the execution proceed instead of
 * failing closed — mirroring the previous "authorization not enabled" short-circuit. This
 * permissive default can be flipped to fail-closed by setting {@code authz.strict=true} (with
 * {@code authz.enabled=true}): the task then returns a {@code TERMINAL}, unauthorized result when
 * no verified token is available rather than allowing the execution.
 */
@Component
public class EvaluatePermissionsTask implements Task {
  private final Logger logger = LoggerFactory.getLogger(EvaluatePermissionsTask.class);

  private final SpinnakerTokenVerifier tokenVerifier;
  private final AuthorizationProperties authorizationProperties;

  @Autowired
  public EvaluatePermissionsTask(
      Optional<SpinnakerTokenVerifier> tokenVerifier,
      Optional<AuthorizationProperties> authorizationProperties) {
    this.tokenVerifier = tokenVerifier.orElse(null);
    this.authorizationProperties = authorizationProperties.orElseGet(AuthorizationProperties::new);
  }

  @Override
  public @Nonnull TaskResult execute(@Nonnull StageExecution stage) {
    EvaluatePermissionsStage.EvaluatePermissionsStageContext context =
        stage.mapTo(EvaluatePermissionsStage.EvaluatePermissionsStageContext.class);

    if (context.getRequiredGroups() == null || context.getRequiredGroups().isEmpty()) {
      logger.info("No required groups specified, allowing execution");
      return TaskResult.builder(ExecutionStatus.SUCCEEDED)
          .context(stage.getContext())
          .outputs(
              java.util.Map.of(
                  "permissionsEvaluated", true,
                  "authorized", true,
                  "reason", "No required groups specified"))
          .build();
    }

    String currentUser = AuthenticatedRequest.getSpinnakerUser().orElse(null);

    Optional<SpinnakerTokenClaims> claims = resolveVerifiedClaims();
    if (claims.isEmpty()) {
      if (authorizationProperties.isEnabled() && authorizationProperties.isStrict()) {
        // Fail closed: the operator has opted into strict authorization but there is no
        // cryptographically verified token to evaluate group membership against.
        logger.warn(
            "Denying execution for user {}: no verified identity token was present and authz.strict is enabled",
            currentUser);
        return TaskResult.builder(ExecutionStatus.TERMINAL)
            .context(stage.getContext())
            .outputs(
                java.util.Map.of(
                    "permissionsEvaluated",
                    true,
                    "authorized",
                    false,
                    "reason",
                    "No verified identity token available and authz.strict is enabled"))
            .build();
      }
      // Permissive: we have no cryptographically verified roles to evaluate against. Don't fail
      // closed during rollout - the same posture as the previous "authorization not enabled"
      // branch.
      logger.info(
          "No verified identity token available for user {}; skipping permission evaluation",
          currentUser);
      return TaskResult.builder(ExecutionStatus.SUCCEEDED)
          .context(stage.getContext())
          .outputs(
              java.util.Map.of(
                  "permissionsEvaluated", false, "reason", "No verified identity token available"))
          .build();
    }

    SpinnakerTokenClaims tokenClaims = claims.get();
    if (currentUser == null) {
      currentUser = tokenClaims.getSubject();
    }

    if (tokenClaims.isAdmin()) {
      logger.info("User {} is an administrator, allowing execution", currentUser);
      return TaskResult.builder(ExecutionStatus.SUCCEEDED)
          .context(stage.getContext())
          .outputs(
              java.util.Map.of(
                  "permissionsEvaluated", true,
                  "authorized", true,
                  "reason", "User is an administrator"))
          .build();
    }

    Set<String> userGroups = new LinkedHashSet<>(tokenClaims.getRoles());
    List<String> requiredGroups = context.getRequiredGroups();

    boolean hasRequiredGroup =
        requiredGroups.stream()
            .anyMatch(
                requiredGroup ->
                    userGroups.stream()
                        .anyMatch(userGroup -> userGroup.equalsIgnoreCase(requiredGroup)));

    if (hasRequiredGroup) {
      logger.info(
          "User {} has required group membership, allowing execution. User groups: {}, Required groups: {}",
          currentUser,
          userGroups,
          requiredGroups);
      return TaskResult.builder(ExecutionStatus.SUCCEEDED)
          .context(stage.getContext())
          .outputs(
              java.util.Map.of(
                  "permissionsEvaluated",
                  true,
                  "authorized",
                  true,
                  "reason",
                  "User has required group membership",
                  "user",
                  currentUser,
                  "userGroups",
                  userGroups,
                  "requiredGroups",
                  requiredGroups))
          .build();
    }

    logger.warn(
        "User {} does not have required group membership. User groups: {}, Required groups: {}",
        currentUser,
        userGroups,
        requiredGroups);
    stage.appendErrorMessage(
        String.format(
            "User %s does not have required group membership. User groups: %s, Required groups: %s",
            currentUser, userGroups, requiredGroups));
    return TaskResult.builder(ExecutionStatus.TERMINAL)
        .context(stage.getContext())
        .outputs(
            java.util.Map.of(
                "permissionsEvaluated",
                true,
                "authorized",
                false,
                "reason",
                "User does not have required group membership",
                "user",
                currentUser,
                "userGroups",
                userGroups,
                "requiredGroups",
                requiredGroups))
        .build();
  }

  /**
   * Resolve the caller's roles from the verified identity token on the current request. Returns
   * empty in permissive situations (no verifier wired, no token present, or an invalid token) so
   * the caller can decide to skip rather than fail closed during rollout.
   */
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
      logger.warn("Ignoring invalid identity token during permission evaluation", e);
      return Optional.empty();
    }
  }
}
