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
import com.netflix.spinnaker.clouddriver.kubernetes.description.JsonPatch;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesPatchOptions;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.op.OperationResult;
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials;
import java.util.HashMap;
import java.util.List;

public interface CanPatch {
  KubernetesKind kind();

  default OperationResult patchWithManifest(
      KubernetesCredentials credentials,
      String namespace,
      String name,
      KubernetesPatchOptions options,
      KubernetesManifest manifest,
      Task task,
      String opName) {
    credentials.patch(kind(), namespace, name, options, manifest, task, opName);
    return patch(namespace, name);
  }

  default OperationResult patchWithJson(
      KubernetesCredentials credentials,
      String namespace,
      String name,
      KubernetesPatchOptions options,
      List<JsonPatch> patches,
      Task task,
      String opName) {
    credentials.patch(kind(), namespace, name, options, patches, task, opName);
    return patch(namespace, name);
  }

  default OperationResult patch(String namespace, String name) {
    KubernetesManifest patchedManifest = new KubernetesManifest();
    patchedManifest.putIfAbsent(
        "metadata", new HashMap<String, Object>()); // Hack: Set mandatory field
    patchedManifest.setNamespace(namespace);
    patchedManifest.setName(name);
    patchedManifest.setKind(kind());
    return new OperationResult().addManifest(patchedManifest);
  }
}
