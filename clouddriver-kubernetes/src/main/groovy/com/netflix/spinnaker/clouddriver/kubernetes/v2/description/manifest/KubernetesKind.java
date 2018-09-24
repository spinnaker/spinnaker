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
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class KubernetesKind {
  public static KubernetesKind API_SERVICE = new KubernetesKind("apiService", false);
  public static KubernetesKind CLUSTER_ROLE = new KubernetesKind("clusterRole", false);
  public static KubernetesKind CLUSTER_ROLE_BINDING = new KubernetesKind("clusterRoleBinding", false);
  public static KubernetesKind CONFIG_MAP = new KubernetesKind("configMap", "cm");
  public static KubernetesKind CONTROLLER_REVISION = new KubernetesKind("controllerRevision");
  public static KubernetesKind CUSTOM_RESOURCE_DEFINITION = new KubernetesKind("customResourceDefinition", "crd", false, false);
  public static KubernetesKind CRON_JOB = new KubernetesKind("cronJob");
  public static KubernetesKind DAEMON_SET = new KubernetesKind("daemonSet", "ds", true, true);
  public static KubernetesKind DEPLOYMENT = new KubernetesKind("deployment", "deploy", true, true);
  public static KubernetesKind EVENT = new KubernetesKind("event");
  public static KubernetesKind HORIZONTAL_POD_AUTOSCALER = new KubernetesKind("horizontalpodautoscaler", "hpa");
  public static KubernetesKind INGRESS = new KubernetesKind("ingress", "ing");
  public static KubernetesKind JOB = new KubernetesKind("job");
  public static KubernetesKind MUTATING_WEBHOOK_CONFIGURATION = new KubernetesKind("mutatingWebhookConfiguration", null, false, false);
  public static KubernetesKind NAMESPACE = new KubernetesKind("namespace", "ns", false, false);
  public static KubernetesKind NETWORK_POLICY = new KubernetesKind("networkPolicy", "netpol", true, true);
  public static KubernetesKind PERSISTENT_VOLUME = new KubernetesKind("persistentVolume", "pv", false, false);
  public static KubernetesKind PERSISTENT_VOLUME_CLAIM = new KubernetesKind("persistentVolumeClaim", "pvc");
  public static KubernetesKind POD = new KubernetesKind("pod", "po", true, true);
  public static KubernetesKind POD_PRESET = new KubernetesKind("podPreset");
  public static KubernetesKind POD_SECURITY_POLICY = new KubernetesKind("podSecurityPolicy");
  public static KubernetesKind POD_DISRUPTION_BUDGET = new KubernetesKind("podDisruptionBudget");
  public static KubernetesKind REPLICA_SET = new KubernetesKind("replicaSet", "rs", true, true);
  public static KubernetesKind ROLE = new KubernetesKind("role", false);
  public static KubernetesKind ROLE_BINDING = new KubernetesKind("roleBinding", false);
  public static KubernetesKind SECRET = new KubernetesKind("secret");
  public static KubernetesKind SERVICE = new KubernetesKind("service", "svc", true, true);
  public static KubernetesKind SERVICE_ACCOUNT = new KubernetesKind("serviceAccount", "sa");
  public static KubernetesKind STATEFUL_SET = new KubernetesKind("statefulSet", null, true, true);
  public static KubernetesKind STORAGE_CLASS = new KubernetesKind("storageClass", "sc", false, false);
  public static KubernetesKind VALIDATING_WEBHOOK_CONFIGURATION = new KubernetesKind("validatingWebhookConfiguration", null, false, false);

  // special kind that should never be assigned to a manifest, used only to represent objects whose kind is not in spinnaker's registry
  public static KubernetesKind NONE = new KubernetesKind("none", null, true, false);

  private final String name;
  private final String alias;
  private boolean isNamespaced;
  // generally reserved for workloads, can be read as "does this belong to a spinnaker cluster?"
  private final boolean hasClusterRelationship;
  // was this kind found after spinnaker started?
  private boolean isDynamic;
  // was this kind added by a user in their clouddriver.yml?
  private boolean isRegistered;

  @Getter
  private static List<KubernetesKind> values;

  protected KubernetesKind(String name, String alias, boolean isNamespaced, boolean hasClusterRelationship) {
    if (values == null) {
      values = Collections.synchronizedList(new ArrayList<>());
    }

    this.name = name;
    this.alias = alias;
    this.isNamespaced = isNamespaced;
    this.hasClusterRelationship = hasClusterRelationship;
    this.isDynamic = false;
    this.isRegistered = true;
    values.add(this);
  }

  protected KubernetesKind(String name) {
    this(name, null, true, false);
  }

  protected KubernetesKind(String name, String alias) {
    this(name, alias, true, false);
  }

  protected KubernetesKind(String name, boolean isNamespaced) {
    this(name, null, isNamespaced, false);
  }

  public boolean isNamespaced() {
    return this.isNamespaced;
  }

  public boolean hasClusterRelationship() {
    return this.hasClusterRelationship;
  }

  public boolean isDynamic() {
    return this.isDynamic;
  }

  public boolean isRegistered() {
    return this.isRegistered;
  }

  @Override
  @JsonValue
  public String toString() {
    return name;
  }

  @JsonCreator
  public static KubernetesKind fromString(String name) {
    return fromString(name, true, true);
  }

  public static KubernetesKind fromString(String name, boolean registered, boolean namespaced) {
    if (StringUtils.isEmpty(name)) {
      return null;
    }

    if (name.equalsIgnoreCase(KubernetesKind.NONE.toString())) {
      throw new IllegalArgumentException("The 'NONE' kind cannot be read.");
    }

    synchronized (values) {
      Optional<KubernetesKind> kindOptional = values.stream()
          .filter(v -> v.name.equalsIgnoreCase(name) || (v.alias != null && v.alias.equalsIgnoreCase(name)))
          .findAny();

      // separate from the above chain to avoid concurrent modification of the values list
      return kindOptional.orElseGet(() -> {
        log.info("Dynamically registering {}, (namespaced: {}, registered: {})", name, namespaced, registered);
        KubernetesKind result = new KubernetesKind(name);
        result.isDynamic = true;
        result.isRegistered = registered;
        result.isNamespaced = namespaced;
        return result;
      });
    }
  }

  public static List<KubernetesKind> registeredStringList(List<String> names) {
    return names.stream()
        .map(KubernetesKind::fromString)
        .collect(Collectors.toList());
  }
}
