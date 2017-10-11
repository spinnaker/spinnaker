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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum KubernetesKind {
  DEPLOYMENT("deployment"),
  INGRESS("ingress"),
  POD("pod"),
  REPLICA_SET("replicaSet"),
  NETWORK_POLICY("networkPolicy"),
  SERVICE("service");

  private final String name;

  KubernetesKind(String name) {
    this.name = name;
  }

  @Override
  @JsonValue
  public String toString() {
    return name;
  }

  @JsonCreator
  public static KubernetesKind fromString(String name) {
    return Arrays.stream(values())
        .filter(v -> v.toString().equalsIgnoreCase(name))
        .findAny()
        .orElseThrow(() -> new IllegalArgumentException("Kubernetes kind '" + name + "' is not supported."));
  }
}
