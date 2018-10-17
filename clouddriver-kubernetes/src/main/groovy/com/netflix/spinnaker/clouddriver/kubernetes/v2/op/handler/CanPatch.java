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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler;

import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesPatchOptions;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.OperationResult;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;

import java.util.HashMap;

public interface CanPatch {
  KubernetesKind kind();

  default OperationResult patch(KubernetesV2Credentials credentials, String namespace, String name,
      KubernetesPatchOptions options, KubernetesManifest manifest) {
    credentials.patch(kind(), namespace, name, options, manifest);

    KubernetesManifest patchedManifest = new KubernetesManifest();
    patchedManifest.putIfAbsent("metadata", new HashMap<String, Object>()); // Hack: Set mandatory field
    patchedManifest.setNamespace(namespace);
    patchedManifest.setName(name);
    patchedManifest.setKind(kind());
    return new OperationResult().addManifest(patchedManifest);
  }
}
