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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class KubernetesKind {
  public static KubernetesKind CONFIG_MAP = new KubernetesKind("configMap", "cm");
  public static KubernetesKind CONTROLLER_REVISION = new KubernetesKind("controllerRevision");
  public static KubernetesKind DAEMON_SET = new KubernetesKind("daemonSet", "ds");
  public static KubernetesKind DEPLOYMENT = new KubernetesKind("deployment", "deploy");
  public static KubernetesKind HORIZONTAL_POD_AUTOSCALER = new KubernetesKind("horizontalpodautoscaler", "hpa");
  public static KubernetesKind INGRESS = new KubernetesKind("ingress", "ing");
  public static KubernetesKind JOB = new KubernetesKind("job");
  public static KubernetesKind POD = new KubernetesKind("pod", "po");
  public static KubernetesKind REPLICA_SET = new KubernetesKind("replicaSet", "rs");
  public static KubernetesKind NAMESPACE = new KubernetesKind("namespace", "ns");
  public static KubernetesKind NETWORK_POLICY = new KubernetesKind("networkPolicy", "netpol");
  public static KubernetesKind PERSISTENT_VOLUME = new KubernetesKind("persistentVolume", "pv");
  public static KubernetesKind PERSISTENT_VOLUME_CLAIM = new KubernetesKind("persistentVolumeClaim", "pvc");
  public static KubernetesKind SECRET = new KubernetesKind("secret");
  public static KubernetesKind SERVICE = new KubernetesKind("service", "svc");
  public static KubernetesKind STATEFUL_SET = new KubernetesKind("statefulSet");

  private final String name;
  private final String alias;

  private static List<KubernetesKind> values;

  protected KubernetesKind(String name, String alias) {
    if (values == null) {
      values = Collections.synchronizedList(new ArrayList<>());
    }

    this.name = name;
    this.alias = alias;
    values.add(this);
  }

  protected KubernetesKind(String name) {
    this(name, null);
  }

  @Override
  @JsonValue
  public String toString() {
    return name;
  }

  @JsonCreator
  public static KubernetesKind fromString(String name) {
    Optional<KubernetesKind> kindOptional = values.stream()
        .filter(v -> v.name.equalsIgnoreCase(name) || (v.alias != null && v.alias.equalsIgnoreCase(name)))
        .findAny();

    // separate from the above chain to avoid concurrent modification of the values list
    return kindOptional.orElseGet(() -> new KubernetesKind(name));
  }

  public static List<KubernetesKind> fromStringList(List<String> names) {
    return names.stream()
        .map(KubernetesKind::fromString)
        .collect(Collectors.toList());
  }
}
