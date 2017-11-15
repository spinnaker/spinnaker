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

import java.util.ArrayList;
import java.util.List;

public class KubernetesKind {
  public static KubernetesKind DAEMON_SET = new KubernetesKind("daemonSet", "ds");
  public static KubernetesKind DEPLOYMENT = new KubernetesKind("deployment", "deploy");
  public static KubernetesKind INGRESS = new KubernetesKind("ingress", "ing");
  public static KubernetesKind POD = new KubernetesKind("pod", "po");
  public static KubernetesKind REPLICA_SET = new KubernetesKind("replicaSet", "rs");
  public static KubernetesKind NAMESPACE = new KubernetesKind("namespace", "ns");
  public static KubernetesKind NETWORK_POLICY = new KubernetesKind("networkPolicy", "netpol");
  public static KubernetesKind SERVICE = new KubernetesKind("service", "svc");
  public static KubernetesKind STATEFUL_SET = new KubernetesKind("statefulSet");

  private final String name;
  private final String alias;

  private static List<KubernetesKind> values;

  private KubernetesKind(String name, String alias) {
    if (values == null) {
      values = new ArrayList<>();
    }

    this.name = name;
    this.alias = alias;
    values.add(this);
  }

  private KubernetesKind(String name) {
    this(name, null);
  }

  @Override
  @JsonValue
  public String toString() {
    return name;
  }

  @JsonCreator
  public static KubernetesKind fromString(String name) {
    return values.stream()
        .filter(v -> v.name.equalsIgnoreCase(name) || (v.alias != null && v.alias.equalsIgnoreCase(name)))
        .findAny()
        .orElseThrow(() -> new IllegalArgumentException("Kubernetes kind '" + name + "' is not supported."));
  }
}
