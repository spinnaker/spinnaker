/*
 * Copyright 2018 Google, Inc.
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

import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourceProperties;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesResourcePropertyRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials;
import java.util.List;

public interface HasPods {
  List<KubernetesManifest> pods(KubernetesV2Credentials credentials, KubernetesManifest object);

  static HasPods lookupProperties(
      KubernetesResourcePropertyRegistry registry, String accountName, KubernetesKind kind) {
    KubernetesResourceProperties hasPodsProperties = registry.get(accountName, kind);
    if (hasPodsProperties == null) {
      throw new IllegalArgumentException(
          "No properties are registered for "
              + kind
              + ", are you sure it's a valid pod manager type?");
    }
    KubernetesHandler hasPodsHandler = hasPodsProperties.getHandler();
    if (hasPodsHandler == null) {
      throw new IllegalArgumentException(
          "No handler registered for " + kind + ", are you sure it's a valid pod manager type?");
    }

    if (!(hasPodsHandler instanceof HasPods)) {
      throw new IllegalArgumentException(
          "No support for pods via " + kind + " exists in Spinnaker");
    }

    return (HasPods) hasPodsHandler;
  }
}
