/*
 * Copyright 2020 Google, Inc.
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.ImmutableSet;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode
public class KubernetesApiGroup {
  // from https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.12/
  public static final KubernetesApiGroup NONE = new KubernetesApiGroup("");
  public static final KubernetesApiGroup CORE = new KubernetesApiGroup("core");
  public static final KubernetesApiGroup BATCH = new KubernetesApiGroup("batch");
  public static final KubernetesApiGroup APPS = new KubernetesApiGroup("apps");
  public static final KubernetesApiGroup EXTENSIONS = new KubernetesApiGroup("extensions");
  public static final KubernetesApiGroup STORAGE_K8S_IO = new KubernetesApiGroup("storage.k8s.io");
  public static final KubernetesApiGroup APIEXTENSIONS_K8S_IO =
      new KubernetesApiGroup("apiextensions.k8s.io");
  public static final KubernetesApiGroup APIREGISTRATION_K8S_IO =
      new KubernetesApiGroup("apiregistration.k8s.io");
  public static final KubernetesApiGroup AUTOSCALING = new KubernetesApiGroup("autoscaling");
  public static final KubernetesApiGroup ADMISSIONREGISTRATION_K8S_IO =
      new KubernetesApiGroup("admissionregistration.k8s.io");
  public static final KubernetesApiGroup POLICY = new KubernetesApiGroup("policy");
  public static final KubernetesApiGroup SCHEDULING_K8S_IO =
      new KubernetesApiGroup("scheduling.k8s.io");
  public static final KubernetesApiGroup SETTINGS_K8S_IO =
      new KubernetesApiGroup("settings.k8s.io");
  public static final KubernetesApiGroup AUTHORIZATION_K8S_IO =
      new KubernetesApiGroup("authorization.k8s.io");
  public static final KubernetesApiGroup AUTHENTICATION_K8S_IO =
      new KubernetesApiGroup("authentication.k8s.io");
  public static final KubernetesApiGroup RBAC_AUTHORIZATION_K8S_IO =
      new KubernetesApiGroup("rbac.authorization.k8s.io");
  public static final KubernetesApiGroup CERTIFICATES_K8S_IO =
      new KubernetesApiGroup("certificates.k8s.io");
  public static final KubernetesApiGroup NETWORKING_K8S_IO =
      new KubernetesApiGroup("networking.k8s.io");

  @Nonnull private final String name;

  // including NONE since it seems like any resource without an api group would have to be native
  private static final ImmutableSet<KubernetesApiGroup> NATIVE_GROUPS =
      ImmutableSet.of(
          CORE,
          BATCH,
          APPS,
          EXTENSIONS,
          STORAGE_K8S_IO,
          APIEXTENSIONS_K8S_IO,
          APIREGISTRATION_K8S_IO,
          AUTOSCALING,
          ADMISSIONREGISTRATION_K8S_IO,
          POLICY,
          SCHEDULING_K8S_IO,
          SETTINGS_K8S_IO,
          AUTHORIZATION_K8S_IO,
          AUTHENTICATION_K8S_IO,
          RBAC_AUTHORIZATION_K8S_IO,
          CERTIFICATES_K8S_IO,
          NETWORKING_K8S_IO,
          NONE);

  private KubernetesApiGroup(@Nonnull String name) {
    this.name = name.toLowerCase();
  }

  @Override
  @JsonValue
  public String toString() {
    return name;
  }

  public boolean isNativeGroup() {
    return NATIVE_GROUPS.contains(this);
  }

  @JsonCreator
  @Nonnull
  public static KubernetesApiGroup fromString(@Nullable String name) {
    if (name == null) {
      return KubernetesApiGroup.NONE;
    }
    return new KubernetesApiGroup(name);
  }
}
