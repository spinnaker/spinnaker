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

import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@EqualsAndHashCode
public class KubernetesApiVersion {
  public static KubernetesApiVersion V1 = new KubernetesApiVersion("v1");
  public static KubernetesApiVersion EXTENSIONS_V1BETA1 = new KubernetesApiVersion("extensions/v1beta1");
  public static KubernetesApiVersion NETWORKING_K8S_IO_V1 = new KubernetesApiVersion("network.k8s.io/v1");
  public static KubernetesApiVersion APPS_V1BETA1 = new KubernetesApiVersion("apps/v1beta1");
  public static KubernetesApiVersion APPS_V1BETA2 = new KubernetesApiVersion("apps/v1beta2");
  public static KubernetesApiVersion BATCH_V1 = new KubernetesApiVersion("batch/v1");

  private final String name;

  private static List<KubernetesApiVersion> values;

  protected KubernetesApiVersion(String name) {
    if (values == null) {
      values = Collections.synchronizedList(new ArrayList<>());
    }

    this.name = name;
    values.add(this);
  }

  @Override
  @JsonValue
  public String toString() {
    return name;
  }

  public KubernetesApiGroup getApiGroup() {
    final String[] split = name.split("/");
    if (split.length > 1) {
      return KubernetesApiGroup.fromString(split[0]);
    }
    return KubernetesApiGroup.NONE;
  }

  @JsonCreator
  public static KubernetesApiVersion fromString(String name) {
    if (StringUtils.isEmpty(name)) {
      return null;
    }

    synchronized (values) {
      Optional<KubernetesApiVersion> versionOptional = values.stream()
          .filter(v -> v.name.equalsIgnoreCase(name))
          .findAny();

      // separate from the above chain to avoid concurrent modification of the values list
      return versionOptional.orElseGet(() -> new KubernetesApiVersion(name));
    }
  }
}
