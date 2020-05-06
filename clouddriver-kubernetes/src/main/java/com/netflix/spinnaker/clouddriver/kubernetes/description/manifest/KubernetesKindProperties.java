/*
 * Copyright 2019 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.description.manifest;

import com.google.common.collect.ImmutableList;
import io.kubernetes.client.openapi.models.V1beta1CustomResourceDefinition;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@EqualsAndHashCode
@ParametersAreNonnullByDefault
@Slf4j
public class KubernetesKindProperties {
  public static List<KubernetesKindProperties> getGlobalKindProperties() {
    return ImmutableList.of(
        new KubernetesKindProperties(KubernetesKind.API_SERVICE, false, false),
        new KubernetesKindProperties(KubernetesKind.CLUSTER_ROLE, false, false),
        new KubernetesKindProperties(KubernetesKind.CLUSTER_ROLE_BINDING, false, false),
        new KubernetesKindProperties(KubernetesKind.CONFIG_MAP, true, false),
        new KubernetesKindProperties(KubernetesKind.CONTROLLER_REVISION, true, false),
        new KubernetesKindProperties(KubernetesKind.CUSTOM_RESOURCE_DEFINITION, false, false),
        new KubernetesKindProperties(KubernetesKind.CRON_JOB, true, false),
        new KubernetesKindProperties(KubernetesKind.DAEMON_SET, true, true),
        new KubernetesKindProperties(KubernetesKind.DEPLOYMENT, true, true),
        new KubernetesKindProperties(KubernetesKind.EVENT, true, false),
        new KubernetesKindProperties(KubernetesKind.HORIZONTAL_POD_AUTOSCALER, true, false),
        new KubernetesKindProperties(KubernetesKind.INGRESS, true, true),
        new KubernetesKindProperties(KubernetesKind.JOB, true, false),
        new KubernetesKindProperties(KubernetesKind.LIMIT_RANGE, true, false),
        new KubernetesKindProperties(KubernetesKind.MUTATING_WEBHOOK_CONFIGURATION, false, false),
        new KubernetesKindProperties(KubernetesKind.NAMESPACE, false, false),
        new KubernetesKindProperties(KubernetesKind.NETWORK_POLICY, true, true),
        new KubernetesKindProperties(KubernetesKind.PERSISTENT_VOLUME, false, false),
        new KubernetesKindProperties(KubernetesKind.PERSISTENT_VOLUME_CLAIM, true, false),
        new KubernetesKindProperties(KubernetesKind.POD, true, false),
        new KubernetesKindProperties(KubernetesKind.POD_PRESET, true, false),
        new KubernetesKindProperties(KubernetesKind.POD_SECURITY_POLICY, false, false),
        new KubernetesKindProperties(KubernetesKind.POD_DISRUPTION_BUDGET, true, false),
        new KubernetesKindProperties(KubernetesKind.REPLICA_SET, true, true),
        new KubernetesKindProperties(KubernetesKind.ROLE, true, false),
        new KubernetesKindProperties(KubernetesKind.ROLE_BINDING, true, false),
        new KubernetesKindProperties(KubernetesKind.SECRET, true, false),
        new KubernetesKindProperties(KubernetesKind.SERVICE, true, true),
        new KubernetesKindProperties(KubernetesKind.SERVICE_ACCOUNT, true, false),
        new KubernetesKindProperties(KubernetesKind.STATEFUL_SET, true, true),
        new KubernetesKindProperties(KubernetesKind.STORAGE_CLASS, false, false),
        new KubernetesKindProperties(KubernetesKind.VALIDATING_WEBHOOK_CONFIGURATION, false, false),
        new KubernetesKindProperties(KubernetesKind.NONE, true, false));
  }

  @Nonnull @Getter private final KubernetesKind kubernetesKind;
  @Getter private final boolean isNamespaced;
  // generally reserved for workloads, can be read as "does this belong to a spinnaker cluster?"
  private final boolean hasClusterRelationship;

  private KubernetesKindProperties(
      KubernetesKind kubernetesKind, boolean isNamespaced, boolean hasClusterRelationship) {
    this.kubernetesKind = kubernetesKind;
    this.isNamespaced = isNamespaced;
    this.hasClusterRelationship = hasClusterRelationship;
  }

  @Nonnull
  public static KubernetesKindProperties withDefaultProperties(KubernetesKind kubernetesKind) {
    return new KubernetesKindProperties(kubernetesKind, true, false);
  }

  @Nonnull
  public static KubernetesKindProperties create(
      KubernetesKind kubernetesKind, boolean isNamespaced) {
    return new KubernetesKindProperties(kubernetesKind, isNamespaced, false);
  }

  @Nonnull
  public static KubernetesKindProperties fromCustomResourceDefinition(
      V1beta1CustomResourceDefinition crd) {
    return create(
        KubernetesKind.fromCustomResourceDefinition(crd),
        crd.getSpec().getScope().equalsIgnoreCase("namespaced"));
  }

  public boolean hasClusterRelationship() {
    return this.hasClusterRelationship;
  }

  public ResourceScope getResourceScope() {
    return isNamespaced ? ResourceScope.NAMESPACE : ResourceScope.CLUSTER;
  }

  public enum ResourceScope {
    CLUSTER,
    NAMESPACE
  }
}
