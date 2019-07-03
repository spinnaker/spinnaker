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
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Slf4j
public final class KubernetesKind {
  private static final KubernetesKindRegistry kindRegistry = new KubernetesKindRegistry();

  public static final KubernetesKind API_SERVICE =
      createAndRegisterKind(
          "apiService", KubernetesApiGroup.APIREGISTRATION_K8S_IO, null, false, false);
  public static final KubernetesKind CLUSTER_ROLE =
      createAndRegisterKind(
          "clusterRole", KubernetesApiGroup.RBAC_AUTHORIZATION_K8S_IO, null, false, false);
  public static final KubernetesKind CLUSTER_ROLE_BINDING =
      createAndRegisterKind(
          "clusterRoleBinding", KubernetesApiGroup.RBAC_AUTHORIZATION_K8S_IO, null, false, false);
  public static final KubernetesKind CONFIG_MAP =
      createAndRegisterKind("configMap", KubernetesApiGroup.CORE, "cm", true, false);
  public static final KubernetesKind CONTROLLER_REVISION =
      createAndRegisterKind("controllerRevision", KubernetesApiGroup.APPS, null, true, false);
  public static final KubernetesKind CUSTOM_RESOURCE_DEFINITION =
      createAndRegisterKind(
          "customResourceDefinition", KubernetesApiGroup.EXTENSIONS, "crd", false, false);
  public static final KubernetesKind CRON_JOB =
      createAndRegisterKind("cronJob", KubernetesApiGroup.BATCH, null, true, false);
  public static final KubernetesKind DAEMON_SET =
      createAndRegisterKind("daemonSet", KubernetesApiGroup.APPS, "ds", true, true);
  public static final KubernetesKind DEPLOYMENT =
      createAndRegisterKind("deployment", KubernetesApiGroup.APPS, "deploy", true, true);
  public static final KubernetesKind EVENT =
      createAndRegisterKind("event", KubernetesApiGroup.CORE, null, true, false);
  public static final KubernetesKind HORIZONTAL_POD_AUTOSCALER =
      createAndRegisterKind(
          "horizontalpodautoscaler", KubernetesApiGroup.AUTOSCALING, "hpa", true, false);
  public static final KubernetesKind INGRESS =
      createAndRegisterKind("ingress", KubernetesApiGroup.EXTENSIONS, "ing", true, true);
  public static final KubernetesKind JOB =
      createAndRegisterKind("job", KubernetesApiGroup.BATCH, null, true, false);
  public static final KubernetesKind MUTATING_WEBHOOK_CONFIGURATION =
      createAndRegisterKind(
          "mutatingWebhookConfiguration",
          KubernetesApiGroup.ADMISSIONREGISTRATION_K8S_IO,
          null,
          false,
          false);
  public static final KubernetesKind NAMESPACE =
      createAndRegisterKind("namespace", KubernetesApiGroup.CORE, "ns", false, false);
  public static final KubernetesKind NETWORK_POLICY =
      createAndRegisterKind("networkPolicy", KubernetesApiGroup.EXTENSIONS, "netpol", true, true);
  public static final KubernetesKind PERSISTENT_VOLUME =
      createAndRegisterKind("persistentVolume", KubernetesApiGroup.CORE, "pv", false, false);
  public static final KubernetesKind PERSISTENT_VOLUME_CLAIM =
      createAndRegisterKind("persistentVolumeClaim", KubernetesApiGroup.CORE, "pvc", true, false);
  public static final KubernetesKind POD =
      createAndRegisterKind("pod", KubernetesApiGroup.CORE, "po", true, false);
  public static final KubernetesKind POD_PRESET =
      createAndRegisterKind("podPreset", KubernetesApiGroup.SETTINGS_K8S_IO, null, true, false);
  public static final KubernetesKind POD_SECURITY_POLICY =
      createAndRegisterKind("podSecurityPolicy", KubernetesApiGroup.EXTENSIONS, null, false, false);
  public static final KubernetesKind POD_DISRUPTION_BUDGET =
      createAndRegisterKind("podDisruptionBudget", KubernetesApiGroup.POLICY, null, true, false);
  public static final KubernetesKind REPLICA_SET =
      createAndRegisterKind("replicaSet", KubernetesApiGroup.APPS, "rs", true, true);
  public static final KubernetesKind ROLE =
      createAndRegisterKind(
          "role", KubernetesApiGroup.RBAC_AUTHORIZATION_K8S_IO, null, true, false);
  public static final KubernetesKind ROLE_BINDING =
      createAndRegisterKind(
          "roleBinding", KubernetesApiGroup.RBAC_AUTHORIZATION_K8S_IO, null, true, false);
  public static final KubernetesKind SECRET =
      createAndRegisterKind("secret", KubernetesApiGroup.CORE, null, true, false);
  public static final KubernetesKind SERVICE =
      createAndRegisterKind("service", KubernetesApiGroup.CORE, "svc", true, true);
  public static final KubernetesKind SERVICE_ACCOUNT =
      createAndRegisterKind("serviceAccount", KubernetesApiGroup.CORE, "sa", true, false);
  public static final KubernetesKind STATEFUL_SET =
      createAndRegisterKind("statefulSet", KubernetesApiGroup.APPS, null, true, true);
  public static final KubernetesKind STORAGE_CLASS =
      createAndRegisterKind("storageClass", KubernetesApiGroup.STORAGE_K8S_IO, "sc", false, false);
  public static final KubernetesKind VALIDATING_WEBHOOK_CONFIGURATION =
      createAndRegisterKind(
          "validatingWebhookConfiguration",
          KubernetesApiGroup.ADMISSIONREGISTRATION_K8S_IO,
          null,
          false,
          false);

  // special kind that should never be assigned to a manifest, used only to represent objects whose
  // kind is not in spinnaker's registry
  public static final KubernetesKind NONE =
      createAndRegisterKind("none", KubernetesApiGroup.NONE, null, true, false);

  @Getter @Nonnull private final String name;
  @EqualsAndHashCode.Include @Nonnull private final String lcName;
  @Getter @Nonnull private final KubernetesApiGroup apiGroup;
  @EqualsAndHashCode.Include @Nullable private final KubernetesApiGroup customApiGroup;

  @Getter @Nullable private final String alias;
  @Getter private final boolean isNamespaced;
  // generally reserved for workloads, can be read as "does this belong to a spinnaker cluster?"
  private final boolean hasClusterRelationship;
  // was this kind found after spinnaker started?
  @Getter private final boolean isDynamic;
  // was this kind added by a user in their clouddriver.yml?
  @Getter private final boolean isRegistered;

  private static KubernetesKind createAndRegisterKind(
      @Nonnull String name,
      @Nonnull KubernetesApiGroup apiGroup,
      @Nullable String alias,
      boolean isNamespaced,
      boolean hasClusterRelationship) {
    return kindRegistry.registerKind(
        new KubernetesKind(
            name, apiGroup, alias, isNamespaced, hasClusterRelationship, false, true));
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

  @Nonnull
  private static ScopedKind parseQualifiedKind(@Nonnull String qualifiedKind) {
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
    return new ScopedKind(kindName, apiGroup);
  }

  @JsonCreator
  @Nonnull
  public static KubernetesKind fromString(@Nonnull final String name) {
    ScopedKind scopedKind = parseQualifiedKind(name);
    return fromString(scopedKind.kindName, scopedKind.apiGroup);
  }

  @Nonnull
  public static KubernetesKind fromString(
      @Nonnull final String name, @Nullable final KubernetesApiGroup apiGroup) {
    return kindRegistry
        .getRegisteredKind(name, apiGroup)
        .orElseGet(
            () ->
                new KubernetesKind(
                    name,
                    Optional.ofNullable(apiGroup).orElse(KubernetesApiGroup.NONE),
                    null,
                    true,
                    false,
                    true,
                    false));
  }

  @Nonnull
  public static KubernetesKind getOrRegisterKind(
      @Nonnull final String name,
      final boolean registered,
      final boolean namespaced,
      @Nullable final KubernetesApiGroup apiGroup) {
    return kindRegistry.getOrRegisterKind(
        name,
        apiGroup,
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

  @Nonnull
  public static KubernetesKind getOrRegisterKind(
      @Nonnull final String qualifiedName, boolean isNamespaced) {
    ScopedKind scopedKind = parseQualifiedKind(qualifiedName);
    return getOrRegisterKind(scopedKind.kindName, true, isNamespaced, scopedKind.apiGroup);
  }

  @Nonnull
  public static List<KubernetesKind> getOrRegisterKinds(@Nonnull List<String> names) {
    return names.stream().map(k -> getOrRegisterKind(k, true)).collect(Collectors.toList());
  }

  @Nonnull
  public static List<KubernetesKind> getRegisteredKinds() {
    return kindRegistry.getRegisteredKinds();
  }

  @RequiredArgsConstructor
  private static class ScopedKind {
    public final String kindName;
    public final KubernetesApiGroup apiGroup;
  }
}
