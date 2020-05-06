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
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesResourceProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesDeleteManifestDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.op.OperationResult;
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesV2Credentials;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.List;

public class KubernetesDeleteManifestOperation implements AtomicOperation<OperationResult> {
  private final KubernetesDeleteManifestDescription description;
  private final KubernetesV2Credentials credentials;
  private static final String OP_NAME = "DELETE_KUBERNETES_MANIFEST";

  public KubernetesDeleteManifestOperation(KubernetesDeleteManifestDescription description) {
    this.description = description;
    this.credentials = description.getCredentials().getCredentials();
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public OperationResult operate(List priorOutputs) {
    getTask().updateStatus(OP_NAME, "Starting delete operation...");
    List<KubernetesCoordinates> coordinates;

    if (description.isDynamic()) {
      coordinates = description.getAllCoordinates();
    } else {
      coordinates = ImmutableList.of(description.getPointCoordinates());
    }

    OperationResult result = new OperationResult();
    coordinates.forEach(
        c -> {
          getTask()
              .updateStatus(OP_NAME, "Looking up resource properties for " + c.getKind() + "...");
          KubernetesResourceProperties properties =
              credentials.getResourcePropertyRegistry().get(c.getKind());
          KubernetesHandler deployer = properties.getHandler();

          if (deployer == null) {
            throw new IllegalArgumentException("Resource with " + c + " does not support delete");
          }

          getTask().updateStatus(OP_NAME, "Calling delete operation...");
          result.merge(
              deployer.delete(
                  credentials,
                  c.getNamespace(),
                  c.getName(),
                  description.getLabelSelectors(),
                  description.getOptions()));
        });

    return result;
  }
}
