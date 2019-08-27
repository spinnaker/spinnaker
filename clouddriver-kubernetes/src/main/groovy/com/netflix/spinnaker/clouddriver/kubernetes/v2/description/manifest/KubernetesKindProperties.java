/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest;

import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@EqualsAndHashCode
@Slf4j
public class KubernetesKindProperties {
  public static List<KubernetesKindProperties> getGlobalKindProperties() {
    return ImmutableList.of(
        new KubernetesKindProperties(KubernetesKind.API_SERVICE, false, false, false),
        new KubernetesKindProperties(KubernetesKind.CLUSTER_ROLE, false, false, false),
        new KubernetesKindProperties(KubernetesKind.CLUSTER_ROLE_BINDING, false, false, false),
        new KubernetesKindProperties(KubernetesKind.CONFIG_MAP, true, false, false),
        new KubernetesKindProperties(KubernetesKind.CONTROLLER_REVISION, true, false, false),
        new KubernetesKindProperties(
            KubernetesKind.CUSTOM_RESOURCE_DEFINITION, false, false, false),
        new KubernetesKindProperties(KubernetesKind.CRON_JOB, true, false, false),
        new KubernetesKindProperties(KubernetesKind.DAEMON_SET, true, true, false),
        new KubernetesKindProperties(KubernetesKind.DEPLOYMENT, true, true, false),
        new KubernetesKindProperties(KubernetesKind.EVENT, true, false, false),
        new KubernetesKindProperties(KubernetesKind.HORIZONTAL_POD_AUTOSCALER, true, false, false),
        new KubernetesKindProperties(KubernetesKind.INGRESS, true, true, false),
        new KubernetesKindProperties(KubernetesKind.JOB, true, false, false),
        new KubernetesKindProperties(
            KubernetesKind.MUTATING_WEBHOOK_CONFIGURATION, false, false, false),
        new KubernetesKindProperties(KubernetesKind.NAMESPACE, false, false, false),
        new KubernetesKindProperties(KubernetesKind.NETWORK_POLICY, true, true, false),
        new KubernetesKindProperties(KubernetesKind.PERSISTENT_VOLUME, false, false, false),
        new KubernetesKindProperties(KubernetesKind.PERSISTENT_VOLUME_CLAIM, true, false, false),
        new KubernetesKindProperties(KubernetesKind.POD, true, false, false),
        new KubernetesKindProperties(KubernetesKind.POD_PRESET, true, false, false),
        new KubernetesKindProperties(KubernetesKind.POD_SECURITY_POLICY, false, false, false),
        new KubernetesKindProperties(KubernetesKind.POD_DISRUPTION_BUDGET, true, false, false),
        new KubernetesKindProperties(KubernetesKind.REPLICA_SET, true, true, false),
        new KubernetesKindProperties(KubernetesKind.ROLE, true, false, false),
        new KubernetesKindProperties(KubernetesKind.ROLE_BINDING, true, false, false),
        new KubernetesKindProperties(KubernetesKind.SECRET, true, false, false),
        new KubernetesKindProperties(KubernetesKind.SERVICE, true, true, false),
        new KubernetesKindProperties(KubernetesKind.SERVICE_ACCOUNT, true, false, false),
        new KubernetesKindProperties(KubernetesKind.STATEFUL_SET, true, true, false),
        new KubernetesKindProperties(KubernetesKind.STORAGE_CLASS, false, false, false),
        new KubernetesKindProperties(
            KubernetesKind.VALIDATING_WEBHOOK_CONFIGURATION, false, false, false),
        new KubernetesKindProperties(KubernetesKind.NONE, true, false, false));
  }

  @Nonnull @Getter private final KubernetesKind kubernetesKind;
  @Getter private final boolean isNamespaced;
  // generally reserved for workloads, can be read as "does this belong to a spinnaker cluster?"
  private final boolean hasClusterRelationship;
  // was this kind found after spinnaker started?
  @Getter private final boolean isDynamic;

  public KubernetesKindProperties(
      @Nonnull KubernetesKind kubernetesKind,
      boolean isNamespaced,
      boolean hasClusterRelationship,
      boolean isDynamic) {
    this.kubernetesKind = kubernetesKind;
    this.isNamespaced = isNamespaced;
    this.hasClusterRelationship = hasClusterRelationship;
    this.isDynamic = isDynamic;
  }

  public static KubernetesKindProperties withDefaultProperties(
      @Nonnull KubernetesKind kubernetesKind) {
    return new KubernetesKindProperties(kubernetesKind, true, false, true);
  }

  public boolean hasClusterRelationship() {
    return this.hasClusterRelationship;
  }
}
