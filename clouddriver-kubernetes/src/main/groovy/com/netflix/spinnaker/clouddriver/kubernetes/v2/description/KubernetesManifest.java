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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.description;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.Map;

public class KubernetesManifest extends HashMap<String, Object> {
  private static <T> T getRequiredField(KubernetesManifest manifest, String field) {
    T res = (T) manifest.get(field);
    if (res == null) {
      throw MalformedManifestException.missingField(manifest, field);
    }

    return res;
  }

  @JsonIgnore
  public KubernetesKind getKind() {
    return KubernetesKind.fromString(getRequiredField(this, "kind"));
  }

  @JsonIgnore
  public KubernetesApiVersion getApiVersion() {
    return KubernetesApiVersion.fromString(getRequiredField(this, "apiVersion"));
  }

  @JsonIgnore
  private Map<String, Object> getMetatdata() {
    return getRequiredField(this, "metadata");
  }

  @JsonIgnore
  public String getName() {
    return (String) getMetatdata().get("name");
  }

  @JsonIgnore
  public String getNamespace() {
    return (String) getMetatdata().get("namespace");
  }

  @JsonIgnore
  public void setNamespace(String namespace) {
    getMetatdata().put("namespace", namespace);
  }

  @JsonIgnore
  public Map<String, String> getAnnotations() {
    Map<String, String> result = (Map<String, String>) getMetatdata().get("annotations");
    if (result == null) {
      result = new HashMap<>();
      getMetatdata().put("annotations", result);
    }

    return result;
  }

  @JsonIgnore
  public String getFullResourceName() {
    return getKind() + "/" + getName();
  }

  public static Pair<KubernetesKind, String> fromFullResourceName(String fullResourceName) {
    String[] split = fullResourceName.split("/");
    if (split.length != 2) {
      throw new IllegalArgumentException("Expected a full resource name of the form <kind>/<name>");
    }

    KubernetesKind kind = KubernetesKind.fromString(split[0]);
    String name = split[1];

    return new ImmutablePair<>(kind, name);
  }
}
