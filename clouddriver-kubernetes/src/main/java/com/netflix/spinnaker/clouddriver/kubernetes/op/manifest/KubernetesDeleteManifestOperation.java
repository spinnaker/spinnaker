/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.op.manifest;

import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesCoordinates;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesDeleteManifestDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.op.OperationResult;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import io.kubernetes.client.openapi.models.V1DeleteOptions;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KubernetesDeleteManifestOperation implements AtomicOperation<OperationResult> {
  private static final Logger log =
      LoggerFactory.getLogger(KubernetesDeleteManifestOperation.class);
  private final KubernetesDeleteManifestDescription description;
  private final KubernetesCredentials credentials;
  private static final String OP_NAME = "DELETE_KUBERNETES_MANIFEST";

  public KubernetesDeleteManifestOperation(KubernetesDeleteManifestDescription description) {
    this.description = description;
    this.credentials = description.getCredentials().getCredentials();
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public OperationResult operate(List<OperationResult> priorOutputs) {
    getTask().updateStatus(OP_NAME, "Starting delete operation...");
    List<KubernetesCoordinates> coordinates;

    if (description.isDynamic()) {
      coordinates = description.getAllCoordinates();
    } else {
      coordinates = ImmutableList.of(description.getPointCoordinates());
    }

    // If "orphanDependents" is strictly defined by the stage then the cascade flag of kubectl
    // delete will honor the setting
    // If orphanDependents isn't set, then look at the value of the "Cascading" delete checkbox in
    // the UI
    V1DeleteOptions deleteOptions = new V1DeleteOptions();
    Map<String, String> options =
        description.getOptions() == null ? new HashMap<>() : description.getOptions();
    if (options.containsKey("orphanDependents")) {
      deleteOptions.setOrphanDependents(options.get("orphanDependents").equalsIgnoreCase("true"));
    } else if (options.containsKey("cascading")) {
      deleteOptions.setOrphanDependents(options.get("cascading").equalsIgnoreCase("false"));
    }
    if (options.containsKey("gracePeriodSeconds")) {
      try {
        deleteOptions.setGracePeriodSeconds(Long.parseLong(options.get("gracePeriodSeconds")));
      } catch (NumberFormatException nfe) {
        log.warn("Unable to parse gracePeriodSeconds; {}", nfe.getMessage());
      }
    }
    OperationResult result = new OperationResult();
    coordinates.forEach(
        c -> {
          getTask()
              .updateStatus(OP_NAME, "Looking up resource properties for " + c.getKind() + "...");
          KubernetesHandler deployer =
              credentials.getResourcePropertyRegistry().get(c.getKind()).getHandler();
          getTask().updateStatus(OP_NAME, "Calling delete operation...");
          result.merge(
              deployer.delete(
                  credentials,
                  c.getNamespace(),
                  c.getName(),
                  description.getLabelSelectors(),
                  deleteOptions));
          getTask()
              .updateStatus(OP_NAME, " delete operation completed successfully for " + c.getName());
        });

    return result;
  }
}
