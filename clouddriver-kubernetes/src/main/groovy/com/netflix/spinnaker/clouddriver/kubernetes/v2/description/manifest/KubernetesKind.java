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
  DEPLOYMENT("deployment", "deploy"),
  INGRESS("ingress", "ing"),
  POD("pod", "po"),
  REPLICA_SET("replicaSet", "rs"),
  NETWORK_POLICY("networkPolicy", "netpol"),
  SERVICE("service", "svc"),
  STATEFUL_SET("statefulset");

  private final String name;
  private final String alias;

  KubernetesKind(String name, String alias) {
    this.name = name;
    this.alias = alias;
  }

  KubernetesKind(String name) {
    this.name = name;
    this.alias = null;
  }

  @Override
  @JsonValue
  public String toString() {
    return name;
  }

  @JsonCreator
  public static KubernetesKind fromString(String name) {
    return Arrays.stream(values())
        .filter(v -> v.name.equalsIgnoreCase(name) || (v.alias != null && v.alias.equalsIgnoreCase(name)))
        .findAny()
        .orElseThrow(() -> new IllegalArgumentException("Kubernetes kind '" + name + "' is not supported."));
  }
}
