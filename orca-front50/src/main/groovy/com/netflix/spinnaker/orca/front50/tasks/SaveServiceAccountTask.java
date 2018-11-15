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

import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.model.resources.ServiceAccount;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.fiat.shared.FiatStatus;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import retrofit.client.Response;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Save a pipeline-scoped Fiat Service Account. The roles from
 * this service account are used for authorization decisions when
 * the pipeline is executed from an automated trigger.
 */

@Component
@Slf4j
public class SaveServiceAccountTask implements RetryableTask {

  private static final String SERVICE_ACCOUNT_SUFFIX = "@managed-service-account";

  @Autowired(required = false)
  private FiatStatus fiatStatus;

  @Autowired(required = false)
  private Front50Service front50Service;

  @Autowired(required = false)
  private FiatPermissionEvaluator fiatPermissionEvaluator;

  @Override
  public long getBackoffPeriod() {
    return TimeUnit.SECONDS.toMillis(1);
  }

  @Override
  public long getTimeout() {
    return TimeUnit.SECONDS.toMillis(30);
  }

  @Nonnull
  @SuppressWarnings("unchecked")
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
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
      return new TaskResult(ExecutionStatus.SUCCEEDED);
    }

    List<String> roles = (List<String>) pipeline.get("roles");
    String user = stage.getExecution().getTrigger().getUser();

    if (!isUserAuthorized(user, roles)) {
      // TODO: Push this to the output result so Deck can show it.
      log.warn("User {} is not authorized with all roles for pipeline", user);
      return new TaskResult(ExecutionStatus.TERMINAL);
    }

    ServiceAccount svcAcct = new ServiceAccount();
    svcAcct.setName(generateSvcAcctName(pipeline));
    svcAcct.setMemberOf(roles);

    // Creating a service account with an existing name will overwrite it
    // i.e. perform an update for our use case
    // TODO(dibyom): If roles are unmodified, skip the create/update below.
    Response response = front50Service.saveServiceAccount(svcAcct);

    if (response.getStatus() != HttpStatus.OK.value()) {
      return new TaskResult(ExecutionStatus.TERMINAL);
    }

    return new TaskResult(
      ExecutionStatus.SUCCEEDED, ImmutableMap.of("pipeline.serviceAccount", svcAcct.getName()));
  }

  private String generateSvcAcctName(Map<String, Object> pipeline) {
    if (pipeline.containsKey("serviceAccount")) {
      return (String) pipeline.get("serviceAccount");
    }
    String pipelineName = (String) pipeline.get("id");
    return pipelineName.toLowerCase() + SERVICE_ACCOUNT_SUFFIX;
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
    Set<String> userRoles = permission.getRoles()
      .stream()
      .map(Role.View::getName)
      .collect(Collectors.toSet());

    return userRoles.containsAll(pipelineRoles);
  }
}
