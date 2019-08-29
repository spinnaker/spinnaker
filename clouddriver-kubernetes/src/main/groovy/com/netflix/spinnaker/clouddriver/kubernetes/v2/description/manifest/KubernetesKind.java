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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class KubernetesKind {
  private static final Map<KubernetesKind, KubernetesKind> aliasMap = new ConcurrentHashMap<>();

  public static final KubernetesKind API_SERVICE =
      createWithAlias("apiService", null, KubernetesApiGroup.APIREGISTRATION_K8S_IO);
  public static final KubernetesKind CLUSTER_ROLE =
      createWithAlias("clusterRole", null, KubernetesApiGroup.RBAC_AUTHORIZATION_K8S_IO);
  public static final KubernetesKind CLUSTER_ROLE_BINDING =
      createWithAlias("clusterRoleBinding", null, KubernetesApiGroup.RBAC_AUTHORIZATION_K8S_IO);
  public static final KubernetesKind CONFIG_MAP =
      createWithAlias("configMap", "cm", KubernetesApiGroup.CORE);
  public static final KubernetesKind CONTROLLER_REVISION =
      createWithAlias("controllerRevision", null, KubernetesApiGroup.APPS);
  public static final KubernetesKind CUSTOM_RESOURCE_DEFINITION =
      createWithAlias("customResourceDefinition", "crd", KubernetesApiGroup.EXTENSIONS);
  public static final KubernetesKind CRON_JOB =
      createWithAlias("cronJob", null, KubernetesApiGroup.BATCH);
  public static final KubernetesKind DAEMON_SET =
      createWithAlias("daemonSet", "ds", KubernetesApiGroup.APPS);
  public static final KubernetesKind DEPLOYMENT =
      createWithAlias("deployment", "deploy", KubernetesApiGroup.APPS);
  public static final KubernetesKind EVENT =
      createWithAlias("event", null, KubernetesApiGroup.CORE);
  public static final KubernetesKind HORIZONTAL_POD_AUTOSCALER =
      createWithAlias("horizontalpodautoscaler", "hpa", KubernetesApiGroup.AUTOSCALING);
  public static final KubernetesKind INGRESS =
      createWithAlias("ingress", null, KubernetesApiGroup.EXTENSIONS);
  public static final KubernetesKind JOB = createWithAlias("job", null, KubernetesApiGroup.BATCH);
  public static final KubernetesKind MUTATING_WEBHOOK_CONFIGURATION =
      createWithAlias(
          "mutatingWebhookConfiguration", null, KubernetesApiGroup.ADMISSIONREGISTRATION_K8S_IO);
  public static final KubernetesKind NAMESPACE =
      createWithAlias("namespace", "ns", KubernetesApiGroup.CORE);
  public static final KubernetesKind NETWORK_POLICY =
      createWithAlias("networkPolicy", "netpol", KubernetesApiGroup.EXTENSIONS);
  public static final KubernetesKind PERSISTENT_VOLUME =
      createWithAlias("persistentVolume", "pv", KubernetesApiGroup.CORE);
  public static final KubernetesKind PERSISTENT_VOLUME_CLAIM =
      createWithAlias("persistentVolumeClaim", "pvc", KubernetesApiGroup.CORE);
  public static final KubernetesKind POD = createWithAlias("pod", "po", KubernetesApiGroup.CORE);
  public static final KubernetesKind POD_PRESET =
      createWithAlias("podPreset", null, KubernetesApiGroup.SETTINGS_K8S_IO);
  public static final KubernetesKind POD_SECURITY_POLICY =
      createWithAlias("podSecurityPolicy", null, KubernetesApiGroup.EXTENSIONS);
  public static final KubernetesKind POD_DISRUPTION_BUDGET =
      createWithAlias("podDisruptionBudget", null, KubernetesApiGroup.POLICY);
  public static final KubernetesKind REPLICA_SET =
      createWithAlias("replicaSet", "rs", KubernetesApiGroup.APPS);
  public static final KubernetesKind ROLE =
      createWithAlias("role", null, KubernetesApiGroup.RBAC_AUTHORIZATION_K8S_IO);
  public static final KubernetesKind ROLE_BINDING =
      createWithAlias("roleBinding", null, KubernetesApiGroup.RBAC_AUTHORIZATION_K8S_IO);
  public static final KubernetesKind SECRET =
      createWithAlias("secret", null, KubernetesApiGroup.CORE);
  public static final KubernetesKind SERVICE =
      createWithAlias("service", "svc", KubernetesApiGroup.CORE);
  public static final KubernetesKind SERVICE_ACCOUNT =
      createWithAlias("serviceAccount", "sa", KubernetesApiGroup.CORE);
  public static final KubernetesKind STATEFUL_SET =
      createWithAlias("statefulSet", null, KubernetesApiGroup.APPS);
  public static final KubernetesKind STORAGE_CLASS =
      createWithAlias("storageClass", "sc", KubernetesApiGroup.STORAGE_K8S_IO);
  public static final KubernetesKind VALIDATING_WEBHOOK_CONFIGURATION =
      createWithAlias(
          "validatingWebhookConfiguration", null, KubernetesApiGroup.ADMISSIONREGISTRATION_K8S_IO);

  // special kind that should never be assigned to a manifest, used only to represent objects whose
  // kind is not in spinnaker's registry
  public static final KubernetesKind NONE = createWithAlias("none", null, KubernetesApiGroup.NONE);

  @Getter @Nonnull private final String name;
  @EqualsAndHashCode.Include @Nonnull private final String lcName;
  @Getter @Nonnull private final KubernetesApiGroup apiGroup;
  @EqualsAndHashCode.Include @Nullable private final KubernetesApiGroup customApiGroup;

  private KubernetesKind(@Nonnull String name, @Nullable KubernetesApiGroup apiGroup) {
    this.name = name;
    this.lcName = name.toLowerCase();
    this.apiGroup = apiGroup == null ? KubernetesApiGroup.NONE : apiGroup;
    if (this.apiGroup.isNativeGroup()) {
      this.customApiGroup = null;
    } else {
      this.customApiGroup = apiGroup;
    }
  }

  private static KubernetesKind createWithAlias(
      @Nonnull String name, @Nullable String alias, @Nullable KubernetesApiGroup apiGroup) {
    KubernetesKind kind = new KubernetesKind(name, apiGroup);
    aliasMap.put(kind, kind);
    if (alias != null) {
      aliasMap.put(new KubernetesKind(alias, apiGroup), kind);
    }
    return kind;
  }

  public static KubernetesKind from(@Nullable String name, @Nullable KubernetesApiGroup apiGroup) {
    if (name == null || name.isEmpty()) {
      return KubernetesKind.NONE;
    }
    KubernetesKind result = new KubernetesKind(name, apiGroup);
    return aliasMap.getOrDefault(result, result);
  }

  @Nonnull
  @JsonCreator
  public static KubernetesKind fromString(@Nonnull String qualifiedKind) {
    KubernetesApiGroup apiGroup;
    String kindName;
    String[] parts = StringUtils.split(qualifiedKind, ".", 2);
    if (parts.length == 2) {
      kindName = parts[0];
      apiGroup = KubernetesApiGroup.fromString(parts[1]);
    } else {
      kindName = qualifiedKind;
      apiGroup = null;
    }
    return from(kindName, apiGroup);
  }

  @Override
  @JsonValue
  public String toString() {
    if (apiGroup.isNativeGroup()) {
      return name;
    }
    return name + "." + apiGroup.toString();
  }
}
