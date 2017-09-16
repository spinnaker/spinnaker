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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
  public void setKind(KubernetesKind kind) {
    put("kind", kind.toString());
  }

  @JsonIgnore
  public KubernetesApiVersion getApiVersion() {
    return KubernetesApiVersion.fromString(getRequiredField(this, "apiVersion"));
  }

  @JsonIgnore
  public void setApiVersion(KubernetesApiVersion apiVersion) {
    put("apiVersion", apiVersion.toString());
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
  public List<OwnerReference> getOwnerReferences(ObjectMapper mapper) {
    Map<String, Object> metadata = getMetatdata();
    Object ownerReferences = metadata.get("ownerReferences");
    if (ownerReferences == null) {
      return new ArrayList<>();
    }

    return mapper.convertValue(ownerReferences, new TypeReference<List<OwnerReference>>() {});
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
  public Optional<Map<String, String>> getSpecTemplateAnnotations() {
    if (!containsKey("spec")) {
      return Optional.empty();
    }

    Map<String, Object> spec = (Map<String, Object>) get("spec");
    if (!spec.containsKey("template")) {
      return Optional.empty();
    }

    Map<String, Object> template = (Map<String, Object>) spec.get("template");
    if (!template.containsKey("metadata")) {
      return Optional.empty();
    }

    Map<String, Object> metadata = (Map<String, Object>) template.get("metadata");
    Map<String, String> result = (Map<String, String>) metadata.get("annotations");
    if (result == null) {
      result = new HashMap<>();
      metadata.put("annotations", result);
    }

    return Optional.of(result);
  }

  @JsonIgnore
  public String getFullResourceName() {
    return String.join("|", getApiVersion().toString(), getKind().toString(), getName());
  }

  public static Triple<KubernetesApiVersion, KubernetesKind, String> fromFullResourceName(String fullResourceName) {
    String[] split = fullResourceName.split("\\|");
    if (split.length != 3) {
      throw new IllegalArgumentException("Expected a full resource name of the form <version>|<kind>|<name>");
    }

    KubernetesApiVersion apiVersion = KubernetesApiVersion.fromString(split[0]);
    KubernetesKind kind = KubernetesKind.fromString(split[1]);
    String name = split[2];

    return new ImmutableTriple<>(apiVersion, kind, name);
  }

  @Data
  public static class OwnerReference {
    KubernetesApiVersion apiVersion;
    KubernetesKind kind;
    String name;
    boolean controller;
  }
}
