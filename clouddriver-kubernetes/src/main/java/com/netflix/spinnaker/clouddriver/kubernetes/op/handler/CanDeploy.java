/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.op.handler;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifestStrategy;
import com.netflix.spinnaker.clouddriver.kubernetes.op.OperationResult;
import com.netflix.spinnaker.clouddriver.kubernetes.op.job.KubectlJobExecutor;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesSelectorList;
import io.kubernetes.client.openapi.models.V1DeleteOptions;
import java.util.ArrayList;
import java.util.List;

public interface CanDeploy {
  default OperationResult deploy(
      KubernetesCredentials credentials,
      KubernetesManifest manifest,
      KubernetesManifestStrategy.DeployStrategy deployStrategy,
      KubernetesManifestStrategy.ServerSideApplyStrategy serverSideApplyStrategy,
      Task task,
      String opName,
      KubernetesSelectorList labelSelectors) {
    // If the manifest has a generateName, we must apply with kubectl create as all other operations
    // require looking up a manifest by name, which will fail.
    if (manifest.hasGenerateName()) {
      KubernetesManifest result = credentials.create(manifest, task, opName, labelSelectors);
      return new OperationResult().addManifest(result);
    }

    KubernetesManifest deployedManifest;
    switch (deployStrategy) {
      case RECREATE:
        try {
          credentials.delete(
              manifest.getKind(),
              manifest.getNamespace(),
              manifest.getName(),
              labelSelectors,
              new V1DeleteOptions(),
              task,
              opName);
        } catch (KubectlJobExecutor.KubectlException ignored) {
        }
        deployedManifest = credentials.deploy(manifest, task, opName, labelSelectors);
        break;
      case REPLACE:
        deployedManifest = credentials.createOrReplace(manifest, task, opName);
        break;
      case SERVER_SIDE_APPLY:
        List<String> cmdArgs = new ArrayList<>();
        cmdArgs.add("--server-side=true");
        if (serverSideApplyStrategy.equals(
            KubernetesManifestStrategy.ServerSideApplyStrategy.FORCE_CONFLICTS)) {
          cmdArgs.add("--force-conflicts=true");
        }
        deployedManifest =
            credentials.deploy(
                manifest,
                task,
                opName,
                labelSelectors,
                cmdArgs.toArray(new String[cmdArgs.size()]));
        break;
      case APPLY:
        deployedManifest = credentials.deploy(manifest, task, opName, labelSelectors);
        break;
      default:
        throw new AssertionError(String.format("Unknown deploy strategy: %s", deployStrategy));
    }
    OperationResult operationResult = new OperationResult();
    if (deployedManifest != null) {
      operationResult.addManifest(deployedManifest);
    }
    return operationResult;
  }
}
