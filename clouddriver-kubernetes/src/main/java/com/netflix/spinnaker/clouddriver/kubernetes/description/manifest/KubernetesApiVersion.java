/*
 * Copyright 2017 Google, Inc.
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
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode
public class KubernetesApiVersion {
  public static final KubernetesApiVersion V1 = new KubernetesApiVersion("v1");
  public static final KubernetesApiVersion EXTENSIONS_V1BETA1 =
      new KubernetesApiVersion("extensions/v1beta1");
  public static final KubernetesApiVersion NETWORKING_K8S_IO_V1 =
      new KubernetesApiVersion("networking.k8s.io/v1");
  public static final KubernetesApiVersion NETWORKING_K8S_IO_V1BETA1 =
      new KubernetesApiVersion("networking.k8s.io/v1beta1");
  public static final KubernetesApiVersion APPS_V1 = new KubernetesApiVersion("apps/v1");
  public static final KubernetesApiVersion APPS_V1BETA1 = new KubernetesApiVersion("apps/v1beta1");
  public static final KubernetesApiVersion APPS_V1BETA2 = new KubernetesApiVersion("apps/v1beta2");
  public static final KubernetesApiVersion BATCH_V1 = new KubernetesApiVersion("batch/v1");
  public static final KubernetesApiVersion NONE = new KubernetesApiVersion("");

  @Nonnull private final String name;
  @Getter @Nonnull @EqualsAndHashCode.Exclude private final KubernetesApiGroup apiGroup;

  private KubernetesApiVersion(@Nonnull String name) {
    this.name = name.toLowerCase();
    this.apiGroup = parseApiGroup(this.name);
  }

  @Override
  @JsonValue
  public String toString() {
    return name;
  }

  @Nonnull
  private static KubernetesApiGroup parseApiGroup(@Nonnull String name) {
    int index = name.indexOf('/');
    if (index > 0) {
      return KubernetesApiGroup.fromString(name.substring(0, index));
    }
    return KubernetesApiGroup.NONE;
  }

  @JsonCreator
  @Nonnull
  public static KubernetesApiVersion fromString(@Nullable String name) {
    if (name == null) {
      return KubernetesApiVersion.NONE;
    }
    return new KubernetesApiVersion(name);
  }
}
