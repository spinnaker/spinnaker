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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler;

import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.OperationResult;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesSelectorList;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import io.kubernetes.client.models.V1DeleteOptions;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public interface CanDelete {
  KubernetesKind kind();

  default OperationResult delete(
      KubernetesV2Credentials credentials,
      String namespace,
      String name,
      KubernetesSelectorList labelSelectors,
      V1DeleteOptions options) {
    options = options == null ? new V1DeleteOptions() : options;
    List<String> deletedNames =
        credentials.delete(kind(), namespace, name, labelSelectors, options);
    OperationResult result = new OperationResult();
    Set<String> fullNames =
        deletedNames.stream()
            .map(n -> KubernetesManifest.getFullResourceName(kind(), n))
            .collect(Collectors.toSet());

    result.setManifestNamesByNamespace(
        new HashMap<>(Collections.singletonMap(namespace, fullNames)));
    return result;
  }
}
