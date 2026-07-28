/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.orca.front50.tasks;

import static java.lang.String.format;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.Hashing;
import com.netflix.spinnaker.kork.common.Header;
import com.netflix.spinnaker.kork.exceptions.UserException;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.front50.model.ServiceAccount;
import com.netflix.spinnaker.orca.front50.pipeline.SavePipelineStage;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import com.netflix.spinnaker.security.token.AuthorizationProperties;
import com.netflix.spinnaker.security.token.SpinnakerTokenClaims;
import com.netflix.spinnaker.security.token.SpinnakerTokenVerifier;
import com.netflix.spinnaker.security.token.TokenValidationException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import retrofit2.Response;

/**
 * Save a pipeline-scoped managed service account. The roles from this service account are used for
 * authorization decisions when the pipeline is executed from an automated trigger.
 *
 * <p>Owner-local / token-carried model: the saving user's roles + admin flag are read from the
 * cryptographically verified identity token on the request ({@link Header#IDENTITY_TOKEN},
 * mirroring {@code EvaluatePermissionsTask}) rather than from a remote {@code getPermission}
 * lookup, and the managed service account's current roles are read from Front50 (its own
 * service-account store). The privilege-escalation guard is preserved: the saving user must hold
 * all roles being assigned to the pipeline (or be an administrator).
 *
 * <p>Authorization disabled / permissive: when no verified identity token is available (no token,
 * no verifier wired, or {@code authz.enabled} still off), the role-subset guard is skipped so the
 * save proceeds instead of failing closed — the same posture as the migrated {@code
 * EvaluatePermissionsTask}. This permissive default can be flipped to fail-closed by setting {@code
 * authz.strict=true} (with {@code authz.enabled=true}): the role-subset guard then denies the save
 * whenever no verified token is present rather than allowing it.
 */
@Component
@Slf4j
public class SaveServiceAccountTask implements RetryableTask {

  private final Front50Service front50Service;
  private final SpinnakerTokenVerifier tokenVerifier;
  private final AuthorizationProperties authorizationProperties;
  private final ObjectMapper objectMapper;
  private final boolean useSharedManagedServiceAccounts;

  @Autowired
  SaveServiceAccountTask(
      Optional<Front50Service> front50Service,
      Optional<SpinnakerTokenVerifier> tokenVerifier,
      Optional<AuthorizationProperties> authorizationProperties,
      ObjectMapper objectMapper,
      @Value("${tasks.use-shared-managed-service-accounts:false}")
          boolean useSharedManagedServiceAccounts) {
    this.front50Service = front50Service.orElse(null);
    this.tokenVerifier = tokenVerifier.orElse(null);
    this.authorizationProperties = authorizationProperties.orElseGet(AuthorizationProperties::new);
    this.objectMapper = objectMapper;
    this.useSharedManagedServiceAccounts = useSharedManagedServiceAccounts;
  }

  @Override
  public long getBackoffPeriod() {
    return TimeUnit.SECONDS.toMillis(1);
  }

  @Override
  public long getTimeout() {
    return TimeUnit.SECONDS.toMillis(60);
  }

  @Nonnull
  @SuppressWarnings("unchecked")
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    if (front50Service == null) {
      throw new UnsupportedOperationException(
          "Front50 is not enabled, no way to save pipeline. Fix this by setting front50.enabled: true");
    }

    boolean isBulkSavingPipelines =
        (boolean) stage.getContext().getOrDefault("isBulkSavingPipelines", false);

    if (isBulkSavingPipelines) {
      return executeBulk(stage);
    } else {
      return executeSingleSave(stage);
    }
  }

  private TaskResult executeSingleSave(@Nonnull StageExecution stage) {
    if (!stage.getContext().containsKey("pipeline")) {
      throw new IllegalArgumentException("pipeline context must be provided");
    }

    if (!(stage.getContext().get("pipeline") instanceof String)) {
      throw new IllegalArgumentException(
          "'pipeline' context key must be a base64-encoded string: Ensure you're on the most recent version of gate");
    }

    Map<String, Object> pipeline;
    try {
      pipeline = (Map<String, Object>) stage.decodeBase64("/pipeline", Map.class);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("pipeline must be encoded as base64", e);
    }

    if (!pipeline.containsKey("roles")) {
      log.debug("Skipping managed service accounts since roles field is not present.");
      return TaskResult.SUCCEEDED;
    }

    List<String> roles = (List<String>) pipeline.get("roles");
    String user = stage.getExecution().getTrigger().getUser();

    Map<String, Object> outputs = new HashMap<>();

    pipeline.computeIfAbsent(
        "id",
        k -> {
          String uuid = UUID.randomUUID().toString();
          outputs.put("pipeline.id", uuid);
          return uuid;
        });

    // Check if pipeline roles did not change, and skip updating a service account if so.
    String serviceAccountName = generateSvcAcctName(pipeline, roles);
    if (!pipelineRolesChanged(serviceAccountName, roles)) {
      log.debug("Skipping managed service account creation/updatimg since roles have not changed.");
      return TaskResult.builder(ExecutionStatus.SUCCEEDED)
          .context(ImmutableMap.of("pipeline.serviceAccount", serviceAccountName))
          .build();
    }

    if (!validateAndSaveServiceAccount(user, serviceAccountName, roles)) {
      return TaskResult.ofStatus(ExecutionStatus.TERMINAL);
    }

    outputs.put("pipeline.serviceAccount", serviceAccountName);

    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).build();
  }

  /**
   * Bulk variant: iterates over the {@code pipelines} list, applying per-pipeline service-account
   * provisioning (id assignment, SA save, trigger {@code runAsUser} rewrite). The mutated list is
   * re-encoded back into the stage context so {@link SavePipelineTask} sees the updated triggers.
   */
  @SuppressWarnings("unchecked")
  private TaskResult executeBulk(StageExecution stage) {
    if (!stage.getContext().containsKey("pipelines")) {
      throw new IllegalArgumentException(
          "pipelines context must be provided when bulk saving pipelines");
    }

    List<Map<String, Object>> pipelines;
    try {
      pipelines = (List<Map<String, Object>>) stage.decodeBase64("/pipelines", List.class);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("pipelines must be encoded as base64", e);
    }

    String user = stage.getExecution().getTrigger().getUser();

    for (Map<String, Object> pipeline : pipelines) {
      if (pipeline.get("id") == null) {
        pipeline.put("id", UUID.randomUUID().toString());
        pipeline.put("regenerateCronTriggerIds", true); // used by front50
      }

      if (!pipeline.containsKey("roles")) {
        continue;
      }
      List<String> roles = (List<String>) pipeline.get("roles");

      String serviceAccountName = generateSvcAcctName(pipeline, roles);
      if (pipelineRolesChanged(serviceAccountName, roles)
          && !validateAndSaveServiceAccount(user, serviceAccountName, roles)) {
        return TaskResult.ofStatus(ExecutionStatus.TERMINAL);
      }
      SavePipelineTask.updateServiceAccount(pipeline, serviceAccountName);
    }

    String reEncoded;
    try {
      reEncoded =
          Base64.getEncoder()
              .encodeToString(
                  objectMapper.writeValueAsString(pipelines).getBytes(StandardCharsets.UTF_8));
    } catch (JsonProcessingException e) {
      throw new RuntimeException(
          "Failed to re-encode pipelines after applying service accounts", e);
    }

    return TaskResult.builder(ExecutionStatus.SUCCEEDED)
        .context(ImmutableMap.of("pipelines", reEncoded))
        .build();
  }

  /**
   * Checks the user is authorized for the given roles, then saves (or updates) a service account
   * with those roles in Front50. Throws {@link UserException} if the user is not authorized;
   * returns {@code false} when Front50 returns a non-OK status (caller translates to {@link
   * ExecutionStatus#TERMINAL}).
   */
  private boolean validateAndSaveServiceAccount(
      String user, String serviceAccountName, List<String> roles) {
    if (!isUserAuthorized(user, roles)) {
      log.warn("User {} is not authorized with all roles for pipeline", user);
      throw new UserException(
          format("User '%s' is not authorized with all roles for pipeline", user));
    }

    ServiceAccount svcAcct = new ServiceAccount();
    svcAcct.setName(serviceAccountName);
    svcAcct.setMemberOf(roles);

    // Creating a service account with an existing name will overwrite it
    // i.e. perform an update for our use case
    Response<ResponseBody> response =
        Retrofit2SyncCall.executeCall(front50Service.saveServiceAccount(svcAcct));

    if (response.code() != HttpStatus.OK.value()) {
      log.warn("Failed to save service account, got response code {}", response.code());
      return false;
    }
    return true;
  }

  private String generateSvcAcctName(Map<String, Object> pipeline, List<String> roles) {
    if (pipeline.containsKey("serviceAccount")) {
      final String serviceAccountName = (String) pipeline.get("serviceAccount");
      /*
       * if useSharedManagedServiceAccounts is disabled right now, but the existing service account name ends with
       * @shared-managed-service-account, then force this pipeline to switch back to a regular managed service account,
       * to avoid inadvertently updating a service account which is shared by multiple pipelines.
       */
      if (useSharedManagedServiceAccounts
          || !usingSharedManagedServiceAccount(serviceAccountName)) {
        return serviceAccountName;
      }
    }

    if (useSharedManagedServiceAccounts) {
      return generateStableSvcAcctNameFromRoles(roles)
          + SavePipelineStage.SHARED_SERVICE_ACCOUNT_SUFFIX;
    }

    String pipelineName = (String) pipeline.get("id");
    return pipelineName.toLowerCase() + SavePipelineStage.SERVICE_ACCOUNT_SUFFIX;
  }

  private boolean usingSharedManagedServiceAccount(String serviceAccountName) {
    return serviceAccountName.endsWith(SavePipelineStage.SHARED_SERVICE_ACCOUNT_SUFFIX);
  }

  private String generateStableSvcAcctNameFromRoles(List<String> roles) {
    String roleString =
        roles.stream()
            .map(String::toLowerCase)
            .distinct()
            .sorted()
            .collect(Collectors.joining("\0"));
    return Hashing.sha256().hashString(roleString, StandardCharsets.UTF_8).toString();
  }

  private boolean isUserAuthorized(String user, List<String> pipelineRoles) {
    if (user == null) {
      return false;
    }

    if (pipelineRoles == null || pipelineRoles.isEmpty()) { // No permissions == everyone can access
      return true;
    }

    Optional<SpinnakerTokenClaims> claims = resolveVerifiedClaims();
    if (claims.isEmpty()) {
      if (authorizationProperties.isEnabled() && authorizationProperties.isStrict()) {
        // Fail closed: the operator has opted into strict authorization but there is no
        // cryptographically verified token to evaluate the role-subset guard against.
        log.warn(
            "Denying managed service account save for user {}: no verified identity token was present and authz.strict is enabled",
            user);
        return false;
      }
      // Permissive: no cryptographically verified roles to evaluate the subset guard against. Don't
      // fail closed during rollout (mirrors EvaluatePermissionsTask / the previous behavior when
      // authorization was disabled).
      log.debug(
          "No verified identity token available for user {}; skipping role-subset guard (permissive)",
          user);
      return true;
    }

    SpinnakerTokenClaims tokenClaims = claims.get();
    if (tokenClaims.isAdmin()) {
      return true;
    }

    // The saving user must hold ALL roles being assigned to the pipeline's service account.
    Set<String> userRoles =
        tokenClaims.getRoles().stream()
            .map(role -> role.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());

    return pipelineRoles.stream()
        .map(role -> role.toLowerCase(Locale.ROOT))
        .allMatch(userRoles::contains);
  }

  /**
   * Whether the managed service account's stored roles differ from {@code pipelineRoles}. The
   * current roles are read from Front50 (the owner of managed service accounts). A service account
   * that does not yet exist (or null roles) is treated as changed so it gets created/updated.
   */
  private boolean pipelineRolesChanged(String serviceAccountName, List<String> pipelineRoles) {
    if (pipelineRoles == null) {
      return true;
    }

    Set<String> currentRoles = getServiceAccountRoles(serviceAccountName);
    if (currentRoles == null) {
      return true;
    }

    Set<String> normalizedCurrent =
        currentRoles.stream().map(r -> r.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    Set<String> normalizedPipeline =
        pipelineRoles.stream().map(r -> r.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());

    return !normalizedCurrent.equals(normalizedPipeline);
  }

  /**
   * Reads the current roles ({@code memberOf}) for a managed service account from Front50's own
   * store. Returns {@code null} when the service account does not exist so the caller treats it as
   * a (new) change.
   */
  private Set<String> getServiceAccountRoles(String serviceAccountName) {
    List<ServiceAccount> serviceAccounts;
    try {
      serviceAccounts = Retrofit2SyncCall.execute(front50Service.getServiceAccounts());
    } catch (Exception e) {
      log.warn(
          "Unable to read managed service accounts from Front50; treating {} as changed",
          serviceAccountName,
          e);
      return null;
    }
    if (serviceAccounts == null) {
      return null;
    }
    return serviceAccounts.stream()
        .filter(sa -> serviceAccountName.equalsIgnoreCase(sa.getName()))
        .findFirst()
        .map(sa -> new HashSet<>(sa.getMemberOf() == null ? List.of() : sa.getMemberOf()))
        .orElse(null);
  }

  /**
   * Resolve the saving user's roles from the verified identity token on the current request.
   * Returns empty in permissive situations (no verifier wired, no token present, or an invalid
   * token) so the caller can skip the subset guard rather than fail closed during rollout.
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
      log.warn("Ignoring invalid identity token while saving managed service account", e);
      return Optional.empty();
    }
  }
}
