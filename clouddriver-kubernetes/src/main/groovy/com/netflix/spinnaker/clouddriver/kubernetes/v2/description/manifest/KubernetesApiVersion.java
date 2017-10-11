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

import java.util.Arrays;

public enum KubernetesApiVersion {
  V1("v1"),
  EXTENSIONS_V1BETA1("extensions/v1beta1"),
  APPS_V1BETA1("apps/v1beta1");

  private final String name;

  KubernetesApiVersion(String name) {
    this.name = name;
  }

  @Override
  @JsonValue
  public String toString() {
    return name;
  }

  @JsonCreator
  public static KubernetesApiVersion fromString(String name) {
    return Arrays.stream(values())
        .filter(v -> v.toString().equalsIgnoreCase(name))
        .findAny()
        .orElseThrow(() -> new IllegalArgumentException("API version " + name + " is not yet supported."));
  }
}
