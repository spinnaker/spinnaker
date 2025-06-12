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

import com.google.common.collect.ImmutableMap;
import com.google.common.hash.Hashing;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.model.resources.ServiceAccount;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.fiat.shared.FiatStatus;
import com.netflix.spinnaker.kork.exceptions.UserException;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.front50.pipeline.SavePipelineStage;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import retrofit2.Response;

/**
 * Save a pipeline-scoped Fiat Service Account. The roles from this service account are used for
 * authorization decisions when the pipeline is executed from an automated trigger.
 */
@Component
@Slf4j
public class SaveServiceAccountTask implements RetryableTask {

  private final FiatStatus fiatStatus;
  private final Front50Service front50Service;
  private final FiatPermissionEvaluator fiatPermissionEvaluator;
  private final boolean useSharedManagedServiceAccounts;

  @Autowired
  SaveServiceAccountTask(
      Optional<FiatStatus> fiatStatus,
      Optional<Front50Service> front50Service,
      Optional<FiatPermissionEvaluator> fiatPermissionEvaluator,
      @Value("${tasks.use-shared-managed-service-accounts:false}")
          boolean useSharedManagedServiceAccounts) {
    this.fiatStatus = fiatStatus.get();
    this.front50Service = front50Service.get();
    this.fiatPermissionEvaluator = fiatPermissionEvaluator.get();
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
    if (!fiatStatus.isEnabled()) {
      throw new UnsupportedOperationException("Fiat is not enabled, cannot save roles.");
    }

    if (front50Service == null) {
      throw new UnsupportedOperationException(
          "Front50 is not enabled, no way to save pipeline. Fix this by setting front50.enabled: true");
    }

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
      return TaskResult.ofStatus(ExecutionStatus.TERMINAL);
    }

    outputs.put("pipeline.serviceAccount", svcAcct.getName());

    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).build();
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

    UserPermission.View permission = fiatPermissionEvaluator.getPermission(user);
    if (permission == null) { // Should never happen?
      return false;
    }

    if (permission.isAdmin()) {
      return true;
    }

    // User has to have all the pipeline roles.
    Set<String> userRoles =
        permission.getRoles().stream().map(Role.View::getName).collect(Collectors.toSet());

    return userRoles.containsAll(pipelineRoles);
  }

  private boolean pipelineRolesChanged(String serviceAccountName, List<String> pipelineRoles) {
    UserPermission.View permission = fiatPermissionEvaluator.getPermission(serviceAccountName);
    if (permission == null || pipelineRoles == null) { // check if user has all permissions
      return true;
    }

    Set<String> currentRoles =
        permission.getRoles().stream().map(Role.View::getName).collect(Collectors.toSet());

    return !currentRoles.equals(new HashSet<>(pipelineRoles));
  }
}
