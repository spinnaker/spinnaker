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
    getTask()
        .updateStatus(
            OP_NAME,
            "Starting delete operation in account " + credentials.getAccountName() + "...");
    List<KubernetesCoordinates> coordinates;

    if (description.isDynamic()) {
      coordinates = description.getAllCoordinates();
    } else {
      coordinates = ImmutableList.of(description.getPointCoordinates());
    }

    // If "orphanDependents" is strictly defined by the stage then the cascade flag of kubectl
    // delete will honor the setting.
    //
    // If orphanDependents isn't set, then look at the value of the delete
    // option.  The "Cascading" delete checkbox in the UI sets it to true/false,
    // but support other values (e.g. foreground/background/orphan) if set
    // directly in the pipeline json.
    V1DeleteOptions deleteOptions = new V1DeleteOptions();
    Map<String, String> options =
        description.getOptions() == null ? new HashMap<>() : description.getOptions();
    if (options.containsKey("orphanDependents")) {
      deleteOptions.setPropagationPolicy(
          options.get("orphanDependents").equalsIgnoreCase("true") ? "orphan" : "background");
    } else if (options.containsKey("cascading")) {
      // For compatibility with pipelines that specify cascading as true/false,
      // map to the appropriate propagation policy.  Clouddriver currently uses
      // kubectl 1.22.17, where --cascade=true/false works, but generates a
      // warning.
      //
      // See
      // https://github.com/kubernetes/kubernetes/blob/v1.22.17/staging/src/k8s.io/kubectl/pkg/cmd/delete/delete_flags.go#L243-L249
      //
      // --cascade=false --> orphan
      // --cascade=true --> background
      String propagationPolicy = null;
      if (options.get("cascading").equalsIgnoreCase("false")) {
        propagationPolicy = "orphan";
      } else if (options.get("cascading").equalsIgnoreCase("true")) {
        propagationPolicy = "background";
      } else {
        propagationPolicy = options.get("cascading");
      }
      deleteOptions.setPropagationPolicy(propagationPolicy);
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
          getTask().updateStatus(OP_NAME, "Calling delete operation for resource" + c + "...");
          result.merge(
              deployer.delete(
                  credentials,
                  c.getNamespace(),
                  c.getName(),
                  description.getLabelSelectors(),
                  deleteOptions,
                  getTask(),
                  OP_NAME));
          getTask()
              .updateStatus(OP_NAME, " delete operation completed successfully for " + c.getName());
        });

    getTask()
        .updateStatus(
            OP_NAME, " delete operation completed successfully for all applicable resources");
    return result;
  }
}
