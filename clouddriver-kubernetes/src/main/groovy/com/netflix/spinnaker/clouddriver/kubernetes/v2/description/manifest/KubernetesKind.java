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
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Slf4j
public final class KubernetesKind {
  @Getter
  private static final List<KubernetesKind> values =
      Collections.synchronizedList(new ArrayList<>());

  public static final KubernetesKind API_SERVICE =
      new KubernetesKind(
          "apiService", KubernetesApiGroup.APIREGISTRATION_K8S_IO, null, false, false);
  public static final KubernetesKind CLUSTER_ROLE =
      new KubernetesKind(
          "clusterRole", KubernetesApiGroup.RBAC_AUTHORIZATION_K8S_IO, null, false, false);
  public static final KubernetesKind CLUSTER_ROLE_BINDING =
      new KubernetesKind(
          "clusterRoleBinding", KubernetesApiGroup.RBAC_AUTHORIZATION_K8S_IO, null, false, false);
  public static final KubernetesKind CONFIG_MAP =
      new KubernetesKind("configMap", KubernetesApiGroup.CORE, "cm", true, false);
  public static final KubernetesKind CONTROLLER_REVISION =
      new KubernetesKind("controllerRevision", KubernetesApiGroup.APPS, null, true, false);
  public static final KubernetesKind CUSTOM_RESOURCE_DEFINITION =
      new KubernetesKind(
          "customResourceDefinition", KubernetesApiGroup.EXTENSIONS, "crd", false, false);
  public static final KubernetesKind CRON_JOB =
      new KubernetesKind("cronJob", KubernetesApiGroup.BATCH, null, true, false);
  public static final KubernetesKind DAEMON_SET =
      new KubernetesKind("daemonSet", KubernetesApiGroup.APPS, "ds", true, true);
  public static final KubernetesKind DEPLOYMENT =
      new KubernetesKind("deployment", KubernetesApiGroup.APPS, "deploy", true, true);
  public static final KubernetesKind EVENT =
      new KubernetesKind("event", KubernetesApiGroup.CORE, null, true, false);
  public static final KubernetesKind HORIZONTAL_POD_AUTOSCALER =
      new KubernetesKind(
          "horizontalpodautoscaler", KubernetesApiGroup.AUTOSCALING, "hpa", true, false);
  public static final KubernetesKind INGRESS =
      new KubernetesKind("ingress", KubernetesApiGroup.EXTENSIONS, "ing", true, true);
  public static final KubernetesKind JOB =
      new KubernetesKind("job", KubernetesApiGroup.BATCH, null, true, false);
  public static final KubernetesKind MUTATING_WEBHOOK_CONFIGURATION =
      new KubernetesKind(
          "mutatingWebhookConfiguration",
          KubernetesApiGroup.ADMISSIONREGISTRATION_K8S_IO,
          null,
          false,
          false);
  public static final KubernetesKind NAMESPACE =
      new KubernetesKind("namespace", KubernetesApiGroup.CORE, "ns", false, false);
  public static final KubernetesKind NETWORK_POLICY =
      new KubernetesKind("networkPolicy", KubernetesApiGroup.EXTENSIONS, "netpol", true, true);
  public static final KubernetesKind PERSISTENT_VOLUME =
      new KubernetesKind("persistentVolume", KubernetesApiGroup.CORE, "pv", false, false);
  public static final KubernetesKind PERSISTENT_VOLUME_CLAIM =
      new KubernetesKind("persistentVolumeClaim", KubernetesApiGroup.CORE, "pvc", true, false);
  public static final KubernetesKind POD =
      new KubernetesKind("pod", KubernetesApiGroup.CORE, "po", true, false);
  public static final KubernetesKind POD_PRESET =
      new KubernetesKind("podPreset", KubernetesApiGroup.SETTINGS_K8S_IO, null, true, false);
  public static final KubernetesKind POD_SECURITY_POLICY =
      new KubernetesKind("podSecurityPolicy", KubernetesApiGroup.EXTENSIONS, null, false, false);
  public static final KubernetesKind POD_DISRUPTION_BUDGET =
      new KubernetesKind("podDisruptionBudget", KubernetesApiGroup.POLICY, null, true, false);
  public static final KubernetesKind REPLICA_SET =
      new KubernetesKind("replicaSet", KubernetesApiGroup.APPS, "rs", true, true);
  public static final KubernetesKind ROLE =
      new KubernetesKind("role", KubernetesApiGroup.RBAC_AUTHORIZATION_K8S_IO, null, true, false);
  public static final KubernetesKind ROLE_BINDING =
      new KubernetesKind(
          "roleBinding", KubernetesApiGroup.RBAC_AUTHORIZATION_K8S_IO, null, true, false);
  public static final KubernetesKind SECRET =
      new KubernetesKind("secret", KubernetesApiGroup.CORE, null, true, false);
  public static final KubernetesKind SERVICE =
      new KubernetesKind("service", KubernetesApiGroup.CORE, "svc", true, true);
  public static final KubernetesKind SERVICE_ACCOUNT =
      new KubernetesKind("serviceAccount", KubernetesApiGroup.CORE, "sa", true, false);
  public static final KubernetesKind STATEFUL_SET =
      new KubernetesKind("statefulSet", KubernetesApiGroup.APPS, null, true, true);
  public static final KubernetesKind STORAGE_CLASS =
      new KubernetesKind("storageClass", KubernetesApiGroup.STORAGE_K8S_IO, "sc", false, false);
  public static final KubernetesKind VALIDATING_WEBHOOK_CONFIGURATION =
      new KubernetesKind(
          "validatingWebhookConfiguration",
          KubernetesApiGroup.ADMISSIONREGISTRATION_K8S_IO,
          null,
          false,
          false);

  // special kind that should never be assigned to a manifest, used only to represent objects whose
  // kind is not in spinnaker's registry
  public static final KubernetesKind NONE =
      new KubernetesKind("none", KubernetesApiGroup.NONE, null, true, false);

  @Nonnull private final String name;
  @EqualsAndHashCode.Include @Nonnull private final String lcName;
  @Nonnull private final KubernetesApiGroup apiGroup;
  @EqualsAndHashCode.Include @Nullable private final KubernetesApiGroup customApiGroup;

  @Nullable private final String alias;
  @Getter private final boolean isNamespaced;
  // generally reserved for workloads, can be read as "does this belong to a spinnaker cluster?"
  private final boolean hasClusterRelationship;
  // was this kind found after spinnaker started?
  @Getter private final boolean isDynamic;
  // was this kind added by a user in their clouddriver.yml?
  @Getter private final boolean isRegistered;

  private KubernetesKind(
      @Nonnull String name,
      @Nonnull KubernetesApiGroup apiGroup,
      @Nullable String alias,
      boolean isNamespaced,
      boolean hasClusterRelationship) {
    this(name, apiGroup, alias, isNamespaced, hasClusterRelationship, false, true);
  }

  private KubernetesKind(
      @Nonnull String name,
      @Nonnull KubernetesApiGroup apiGroup,
      @Nullable String alias,
      boolean isNamespaced,
      boolean hasClusterRelationship,
      boolean isDynamic,
      boolean isRegistered) {
    this.name = name;
    this.lcName = name.toLowerCase();
    this.apiGroup = apiGroup;
    if (apiGroup.isNativeGroup()) {
      this.customApiGroup = null;
    } else {
      this.customApiGroup = apiGroup;
    }
    this.alias = alias;
    this.isNamespaced = isNamespaced;
    this.hasClusterRelationship = hasClusterRelationship;
    this.isDynamic = isDynamic;
    this.isRegistered = isRegistered;
    values.add(this);
  }

  public boolean hasClusterRelationship() {
    return this.hasClusterRelationship;
  }

  @Override
  @JsonValue
  public String toString() {
    if (apiGroup.isNativeGroup()) {
      return name;
    }
    return name + "." + apiGroup.toString();
  }

  @JsonCreator
  @Nonnull
  public static KubernetesKind fromString(@Nonnull String name) {
    return fromString(name, true);
  }

  @Nonnull
  public static KubernetesKind fromString(@Nonnull String name, boolean namespaced) {
    KubernetesApiGroup apiGroup;
    String kindName;
    String[] parts = StringUtils.split(name, ".", 2);
    if (parts.length == 2) {
      kindName = parts[0];
      apiGroup = KubernetesApiGroup.fromString(parts[1]);
    } else {
      kindName = name;
      apiGroup = null;
    }
    return KubernetesKind.getOrRegisterKind(kindName, true, namespaced, apiGroup);
  }

  @Nonnull
  public static KubernetesKind getOrRegisterKind(
      @Nonnull final String name,
      final boolean registered,
      final boolean namespaced,
      @Nullable final KubernetesApiGroup apiGroup) {
    if (StringUtils.isEmpty(name)) {
      return KubernetesKind.NONE;
    }

    if (name.equalsIgnoreCase(KubernetesKind.NONE.toString())) {
      throw new IllegalArgumentException("The 'NONE' kind cannot be read.");
    }

    Predicate<KubernetesKind> groupMatches =
        kind -> {
          // Exact match
          if (Objects.equals(kind.apiGroup, apiGroup)) {
            return true;
          }

          // If we have not specified an API group, default to finding a native kind that matches
          if (apiGroup == null || apiGroup.isNativeGroup()) {
            return kind.apiGroup.isNativeGroup();
          }

          return false;
        };

    synchronized (values) {
      Optional<KubernetesKind> kindOptional =
          values.stream()
              .filter(
                  v ->
                      v.name.equalsIgnoreCase(name)
                          || (v.alias != null && v.alias.equalsIgnoreCase(name)))
              .filter(groupMatches)
              .findAny();

      // separate from the above chain to avoid concurrent modification of the values list
      return kindOptional.orElseGet(
          () -> {
            log.info(
                "Dynamically registering {}, (namespaced: {}, registered: {})",
                name,
                namespaced,
                registered);
            return new KubernetesKind(
                name,
                Optional.ofNullable(apiGroup).orElse(KubernetesApiGroup.NONE),
                null,
                namespaced,
                false,
                true,
                registered);
          });
    }
  }

  public static List<KubernetesKind> registeredStringList(List<String> names) {
    return names.stream().map(KubernetesKind::fromString).collect(Collectors.toList());
  }
}
