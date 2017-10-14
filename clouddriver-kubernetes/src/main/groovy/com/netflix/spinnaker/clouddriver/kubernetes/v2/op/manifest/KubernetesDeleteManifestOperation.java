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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesCoordinates;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourceProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesDeleteManifestDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.deployer.CanDelete;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.deployer.KubernetesDeployer;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;

import java.util.List;
import java.util.Map;

public class KubernetesDeleteManifestOperation implements AtomicOperation<Void> {
  private final KubernetesDeleteManifestDescription description;
  private final KubernetesV2Credentials credentials;
  private final KubernetesResourcePropertyRegistry registry;
  private final ObjectMapper mapper = new ObjectMapper();
  private static final String OP_NAME = "DELETE_KUBERNETES_MANIFEST";

  public KubernetesDeleteManifestOperation(KubernetesDeleteManifestDescription description, KubernetesResourcePropertyRegistry registry) {
    this.description = description;
    this.credentials = (KubernetesV2Credentials) description.getCredentials().getCredentials();
    this.registry = registry;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public Void operate(List priorOutputs) {
    getTask().updateStatus(OP_NAME, "Starting delete operation...");
    KubernetesCoordinates coordinates = description.getCoordinates();

    getTask().updateStatus(OP_NAME, "Looking up resource properties...");
    KubernetesResourceProperties properties = registry.lookup(coordinates);
    KubernetesDeployer deployer = properties.getDeployer();

    if (!(deployer instanceof CanDelete)) {
      throw new IllegalArgumentException("Resource with " + coordinates + " does not support delete");
    }

    CanDelete canDelete = (CanDelete) deployer;

    getTask().updateStatus(OP_NAME, "Calling delete operation...");
    Map deleteOptions = description.getDeleteOptions();
    Object convertedDeleteOptions = deleteOptions == null ? null : mapper.convertValue(deleteOptions, (canDelete).getDeleteOptionsClass());
    canDelete.delete(credentials,
        coordinates.getNamespace(),
        coordinates.getName(),
        convertedDeleteOptions);

    return null;
  }
}
