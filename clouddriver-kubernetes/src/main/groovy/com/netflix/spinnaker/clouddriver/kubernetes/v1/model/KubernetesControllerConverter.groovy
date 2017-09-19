/*
 * Copyright 2017 Cisco, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.v1.model

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.kubernetes.client.models.V1ObjectMeta

class KubernetesControllerConverter implements HasMetadata {
  String kind
  String apiVersion
  ObjectMeta metadata

  KubernetesControllerConverter(String kind, String apiVersion, V1ObjectMeta metadata) {
    this.kind = kind
    this.apiVersion = apiVersion

    this.metadata = new ObjectMeta()
    this.metadata.name = metadata.name
    this.metadata.namespace = metadata.namespace
    setMetadata(this.metadata)
  }

  @Override
  ObjectMeta getMetadata() {
    return this.metadata
  }

  @Override
  void setMetadata(ObjectMeta metadata) {
    this.metadata = metadata
  }

  @Override
  String getKind() {
    return this.kind
  }

  @Override
  String getApiVersion() {
    return this.apiVersion
  }
}


