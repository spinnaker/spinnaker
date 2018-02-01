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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@EqualsAndHashCode
public class KubernetesApiVersion {
  public static KubernetesApiVersion V1 = new KubernetesApiVersion("v1");
  public static KubernetesApiVersion EXTENSIONS_V1BETA1 = new KubernetesApiVersion("extensions/v1beta1");
  public static KubernetesApiVersion NETWORKING_K8S_IO_V1 = new KubernetesApiVersion("network.k8s.io/v1");
  public static KubernetesApiVersion APPS_V1BETA1 = new KubernetesApiVersion("apps/v1beta1");
  public static KubernetesApiVersion APPS_V1BETA2 = new KubernetesApiVersion("apps/v1beta2");

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

  @JsonCreator
  public static KubernetesApiVersion fromString(String name) {
    return values.stream()
        .filter(v -> v.name.equalsIgnoreCase(name))
        .findAny()
        .orElse(new KubernetesApiVersion(name));
  }
}
