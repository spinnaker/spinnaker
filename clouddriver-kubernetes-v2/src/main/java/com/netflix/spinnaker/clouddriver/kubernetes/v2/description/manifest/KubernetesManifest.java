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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

public class KubernetesManifest extends HashMap<String, Object> {
  private static final ObjectMapper mapper = new ObjectMapper();

  @Nullable private KubernetesKind computedKind;

  @Override
  public KubernetesManifest clone() {
    return (KubernetesManifest) super.clone();
  }

  private static <T> T getRequiredField(KubernetesManifest manifest, String field) {
    T res = (T) manifest.get(field);
    if (res == null) {
      throw MalformedManifestException.missingField(manifest, field);
    }

    return res;
  }

  @JsonIgnore
  @Nonnull
  public KubernetesKind getKind() {
    if (computedKind == null) {
      computedKind = computeKind();
    }
    return computedKind;
  }

  @Nonnull
  private KubernetesKind computeKind() {
    // using ApiVersion here allows a translation from a kind of NetworkPolicy in the manifest to
    // something
    // like  NetworkPolicy.crd.projectcalico.org for custom resources
    String kindName = getKindName();
    KubernetesApiGroup kubernetesApiGroup;
    if (this.containsKey("apiVersion")) {
      kubernetesApiGroup = getApiVersion().getApiGroup();
    } else {
      kubernetesApiGroup = null;
    }
    return KubernetesKind.from(kindName, kubernetesApiGroup);
  }

  @JsonIgnore
  public String getKindName() {
    return getRequiredField(this, "kind");
  }

  @JsonIgnore
  public void setKind(KubernetesKind kind) {
    put("kind", kind.toString());
    computedKind = null;
  }

  @JsonIgnore
  public KubernetesApiVersion getApiVersion() {
    return KubernetesApiVersion.fromString(getRequiredField(this, "apiVersion"));
  }

  @JsonIgnore
  public void setApiVersion(KubernetesApiVersion apiVersion) {
    put("apiVersion", apiVersion.toString());
    computedKind = null;
  }

  @JsonIgnore
  private Map<String, Object> getMetadata() {
    return getRequiredField(this, "metadata");
  }

  @JsonIgnore
  public String getName() {
    return (String) getMetadata().get("name");
  }

  @JsonIgnore
  public String getUid() {
    return (String) getMetadata().get("uid");
  }

  @JsonIgnore
  public void setName(String name) {
    getMetadata().put("name", name);
  }

  @JsonIgnore
  public String getNamespace() {
    String namespace = (String) getMetadata().get("namespace");
    return StringUtils.isEmpty(namespace) ? "" : namespace;
  }

  @JsonIgnore
  public void setNamespace(String namespace) {
    getMetadata().put("namespace", namespace);
  }

  @JsonIgnore
  public String getCreationTimestamp() {
    return getMetadata().containsKey("creationTimestamp")
        ? getMetadata().get("creationTimestamp").toString()
        : "";
  }

  @JsonIgnore
  public List<OwnerReference> getOwnerReferences() {
    Map<String, Object> metadata = getMetadata();
    Object ownerReferences = metadata.get("ownerReferences");
    if (ownerReferences == null) {
      return new ArrayList<>();
    }

    return mapper.convertValue(ownerReferences, new TypeReference<List<OwnerReference>>() {});
  }

  @JsonIgnore
  public KubernetesManifestSelector getManifestSelector() {
    if (!containsKey("spec")) {
      return null;
    }

    Map<String, Object> spec = (Map<String, Object>) get("spec");
    if (!spec.containsKey("selector")) {
      return null;
    }

    Map<String, Object> selector = (Map<String, Object>) spec.get("selector");
    if (!selector.containsKey("matchExpressions") && !selector.containsKey("matchLabels")) {
      return new KubernetesManifestSelector()
          .setMatchLabels((Map<String, String>) spec.get("selector"));
    } else {
      return mapper.convertValue(selector, KubernetesManifestSelector.class);
    }
  }

  @JsonIgnore
  public Map<String, String> getLabels() {
    Map<String, String> result = (Map<String, String>) getMetadata().get("labels");
    if (result == null) {
      result = new HashMap<>();
      getMetadata().put("labels", result);
    }

    return result;
  }

  @JsonIgnore
  public Map<String, String> getAnnotations() {
    Map<String, String> result = (Map<String, String>) getMetadata().get("annotations");
    if (result == null) {
      result = new HashMap<>();
      getMetadata().put("annotations", result);
    }

    return result;
  }

  @JsonIgnore
  public Double getReplicas() {
    if (!containsKey("spec")) {
      return null;
    }

    Map<String, Object> spec = (Map<String, Object>) get("spec");
    if (!spec.containsKey("replicas")) {
      return null;
    }
    return (Double) spec.get("replicas");
  }

  @JsonIgnore
  public void setReplicas(Double replicas) {
    if (!containsKey("spec")) {
      return;
    }

    Map<String, Object> spec = (Map<String, Object>) get("spec");
    if (!spec.containsKey("replicas")) {
      return;
    }
    spec.put("replicas", replicas);
  }

  @JsonIgnore
  public Optional<Map<String, String>> getSpecTemplateLabels() {
    if (!containsKey("spec")) {
      return Optional.empty();
    }

    Map<String, Object> spec = (Map<String, Object>) get("spec");
    if (!spec.containsKey("template")) {
      return Optional.empty();
    }

    if (!(spec.get("template") instanceof Map)) {
      return Optional.empty();
    }

    Map<String, Object> template = (Map<String, Object>) spec.get("template");
    if (!template.containsKey("metadata")) {
      return Optional.empty();
    }

    Map<String, Object> metadata = (Map<String, Object>) template.get("metadata");
    if (metadata == null) {
      return Optional.empty();
    }

    Map<String, String> result = (Map<String, String>) metadata.get("labels");
    if (result == null) {
      result = new HashMap<>();
      metadata.put("labels", result);
    }

    return Optional.of(result);
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

    if (!(spec.get("template") instanceof Map)) {
      return Optional.empty();
    }

    Map<String, Object> template = (Map<String, Object>) spec.get("template");
    if (!template.containsKey("metadata")) {
      return Optional.empty();
    }

    Map<String, Object> metadata = (Map<String, Object>) template.get("metadata");
    if (metadata == null) {
      return Optional.empty();
    }

    Map<String, String> result = (Map<String, String>) metadata.get("annotations");
    if (result == null) {
      result = new HashMap<>();
      metadata.put("annotations", result);
    }

    return Optional.of(result);
  }

  @JsonIgnore
  public Object getStatus() {
    return get("status");
  }

  @JsonIgnore
  public int getObservedGeneration() {
    Object statusObj = getStatus();
    if (!(statusObj instanceof Map)) {
      throw new IllegalStateException(
          "Expected status to be a Map but was actually a " + statusObj.getClass());
    }

    Map<String, Object> status = (Map<String, Object>) statusObj;

    Object observedGenObj = status.get("observedGeneration");

    if (!(observedGenObj instanceof Number)) {
      throw new IllegalStateException(
          "Expected status.observedGeneration to be a Number but was actually a "
              + observedGenObj.getClass());
    }
    return ((Number) observedGenObj).intValue();
  }

  @JsonIgnore
  public int getGeneration() {
    Object generationObj = getMetadata().get("generation");
    if (!(generationObj instanceof Number)) {
      throw new IllegalStateException(
          "Expected metadata.generation to be a Number but was actually a "
              + generationObj.getClass());
    }
    return ((Number) generationObj).intValue();
  }

  @JsonIgnore
  public String getFullResourceName() {
    return getFullResourceName(getKind(), getName());
  }

  public static String getFullResourceName(KubernetesKind kind, String name) {
    return String.join(" ", kind.toString(), name);
  }

  @JsonIgnore
  public boolean isNewerThanObservedGeneration() {
    int generation = getGeneration();
    int observedGeneration = getObservedGeneration();

    return generation > observedGeneration;
  }

  /*
   * The reasoning behind removing metadata for comparison is that it shouldn't affect the runtime behavior
   * of the resource we are creating.
   */
  public boolean nonMetadataEquals(KubernetesManifest other) {
    if (other == null) {
      return false;
    }

    KubernetesManifest cloneThis = this.clone();
    KubernetesManifest cloneOther = other.clone();

    cloneThis.remove("metadata");
    cloneOther.remove("metadata");

    return cloneThis.equals(cloneOther);
  }

  public static Pair<KubernetesKind, String> fromFullResourceName(String fullResourceName) {
    String[] split = fullResourceName.split(" ");
    if (split.length != 2) {
      throw new IllegalArgumentException("Expected a full resource name of the form <kind> <name>");
    }

    KubernetesKind kind = KubernetesKind.fromString(split[0]);
    String name = split[1];

    return new ImmutablePair<>(kind, name);
  }

  @Data
  public static class OwnerReference {
    KubernetesApiVersion apiVersion;
    KubernetesKind kind;
    String name;
    String uid;
    boolean blockOwnerDeletion;
    boolean controller;
  }
}
