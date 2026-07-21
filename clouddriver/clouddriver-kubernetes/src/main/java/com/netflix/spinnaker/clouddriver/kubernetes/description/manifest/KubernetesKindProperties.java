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
import io.kubernetes.client.openapi.models.V1CustomResourceDefinition;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode
@ParametersAreNonnullByDefault
public class KubernetesKindProperties {
  public static List<KubernetesKindProperties> getGlobalKindProperties() {
    return ImmutableList.of(
        new KubernetesKindProperties(KubernetesKind.API_SERVICE, false),
        new KubernetesKindProperties(KubernetesKind.CLUSTER_ROLE, false),
        new KubernetesKindProperties(KubernetesKind.CLUSTER_ROLE_BINDING, false),
        new KubernetesKindProperties(KubernetesKind.CONFIG_MAP, true),
        new KubernetesKindProperties(KubernetesKind.CONTROLLER_REVISION, true),
        new KubernetesKindProperties(KubernetesKind.CUSTOM_RESOURCE_DEFINITION, false),
        new KubernetesKindProperties(KubernetesKind.CRON_JOB, true),
        new KubernetesKindProperties(KubernetesKind.DAEMON_SET, true),
        new KubernetesKindProperties(KubernetesKind.DEPLOYMENT, true),
        new KubernetesKindProperties(KubernetesKind.EVENT, true),
        new KubernetesKindProperties(KubernetesKind.HORIZONTAL_POD_AUTOSCALER, true),
        new KubernetesKindProperties(KubernetesKind.INGRESS, true),
        new KubernetesKindProperties(KubernetesKind.JOB, true),
        new KubernetesKindProperties(KubernetesKind.LIMIT_RANGE, true),
        new KubernetesKindProperties(KubernetesKind.MUTATING_WEBHOOK_CONFIGURATION, false),
        new KubernetesKindProperties(KubernetesKind.NAMESPACE, false),
        new KubernetesKindProperties(KubernetesKind.NETWORK_POLICY, true),
        new KubernetesKindProperties(KubernetesKind.PERSISTENT_VOLUME, false),
        new KubernetesKindProperties(KubernetesKind.PERSISTENT_VOLUME_CLAIM, true),
        new KubernetesKindProperties(KubernetesKind.POD, true),
        new KubernetesKindProperties(KubernetesKind.POD_PRESET, true),
        new KubernetesKindProperties(KubernetesKind.POD_SECURITY_POLICY, false),
        new KubernetesKindProperties(KubernetesKind.POD_DISRUPTION_BUDGET, true),
        new KubernetesKindProperties(KubernetesKind.REPLICA_SET, true),
        new KubernetesKindProperties(KubernetesKind.ROLE, true),
        new KubernetesKindProperties(KubernetesKind.ROLE_BINDING, true),
        new KubernetesKindProperties(KubernetesKind.SECRET, true),
        new KubernetesKindProperties(KubernetesKind.SERVICE, true),
        new KubernetesKindProperties(KubernetesKind.SERVICE_ACCOUNT, true),
        new KubernetesKindProperties(KubernetesKind.STATEFUL_SET, true),
        new KubernetesKindProperties(KubernetesKind.STORAGE_CLASS, false),
        new KubernetesKindProperties(KubernetesKind.VALIDATING_WEBHOOK_CONFIGURATION, false),
        new KubernetesKindProperties(KubernetesKind.NONE, true));
  }

  @Nonnull @Getter private final KubernetesKind kubernetesKind;
  @Getter private final boolean isNamespaced;

  private KubernetesKindProperties(KubernetesKind kubernetesKind, boolean isNamespaced) {
    this.kubernetesKind = kubernetesKind;
    this.isNamespaced = isNamespaced;
  }

  @Nonnull
  public static KubernetesKindProperties withDefaultProperties(KubernetesKind kubernetesKind) {
    return new KubernetesKindProperties(kubernetesKind, true);
  }

  @Nonnull
  public static KubernetesKindProperties create(
      KubernetesKind kubernetesKind, boolean isNamespaced) {
    return new KubernetesKindProperties(kubernetesKind, isNamespaced);
  }

  @Nonnull
  public static KubernetesKindProperties fromCustomResourceDefinition(
      V1CustomResourceDefinition crd) {
    return create(
        KubernetesKind.fromCustomResourceDefinition(crd),
        crd.getSpec().getScope().equalsIgnoreCase("namespaced"));
  }

  public ResourceScope getResourceScope() {
    return isNamespaced ? ResourceScope.NAMESPACE : ResourceScope.CLUSTER;
  }

  public enum ResourceScope {
    CLUSTER,
    NAMESPACE
  }
}
