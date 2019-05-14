/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.op.manifest;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesCoordinates;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourceProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesDeleteManifestDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.OperationResult;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.CanDelete;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesHandler;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.Collections;
import java.util.List;

public class KubernetesDeleteManifestOperation implements AtomicOperation<OperationResult> {
  private final KubernetesDeleteManifestDescription description;
  private final KubernetesV2Credentials credentials;
  private final KubernetesResourcePropertyRegistry registry;
  private final String accountName;
  private static final String OP_NAME = "DELETE_KUBERNETES_MANIFEST";

  public KubernetesDeleteManifestOperation(
      KubernetesDeleteManifestDescription description,
      KubernetesResourcePropertyRegistry registry) {
    this.description = description;
    this.credentials = (KubernetesV2Credentials) description.getCredentials().getCredentials();
    this.accountName = description.getCredentials().getName();
    this.registry = registry;
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
      coordinates = Collections.singletonList(description.getPointCoordinates());
    }

    OperationResult result = new OperationResult();
    coordinates.forEach(
        c -> {
          getTask()
              .updateStatus(OP_NAME, "Looking up resource properties for " + c.getKind() + "...");
          KubernetesResourceProperties properties = registry.get(accountName, c.getKind());
          KubernetesHandler deployer = properties.getHandler();

          if (!(deployer instanceof CanDelete)) {
            throw new IllegalArgumentException("Resource with " + c + " does not support delete");
          }

          CanDelete canDelete = (CanDelete) deployer;

          getTask().updateStatus(OP_NAME, "Calling delete operation...");
          result.merge(
              canDelete.delete(
                  credentials,
                  c.getNamespace(),
                  c.getName(),
                  description.getLabelSelectors(),
                  description.getOptions()));
        });

    return result;
  }
}
