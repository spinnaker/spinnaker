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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.kubernetes.description.KubernetesCoordinates;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Data;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KubernetesManifest extends HashMap<String, Object> {
  private static final Logger log = LoggerFactory.getLogger(KubernetesManifest.class);
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
  public boolean hasGenerateName() {
    if (!Strings.isNullOrEmpty(this.getName())) {
      // If a name is present, it will be used instead of a generateName
      return false;
    }
    return !Strings.isNullOrEmpty((String) getMetadata().get("generateName"));
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
  @Nonnull
  public String getNamespace() {
    String namespace = (String) getMetadata().get("namespace");
    return Strings.nullToEmpty(namespace);
  }

  @JsonIgnore
  public void setNamespace(String namespace) {
    getMetadata().put("namespace", namespace);
  }

  @JsonIgnore
  @Nonnull
  public String getCreationTimestamp() {
    return getMetadata().containsKey("creationTimestamp")
        ? getMetadata().get("creationTimestamp").toString()
        : "";
  }

  @JsonIgnore
  @Nullable
  public Long getFormattedCreationTimestamp() {
    String timestamp = getCreationTimestamp();
    try {
      if (!Strings.isNullOrEmpty(timestamp)) {
        return (new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX").parse(timestamp)).getTime();
      }
    } catch (ParseException e) {
      log.warn("Failed to parse timestamp: ", e);
    }

    return null;
  }

  @JsonIgnore
  @Nonnull
  public List<OwnerReference> getOwnerReferences() {
    Map<String, Object> metadata = getMetadata();
    return Optional.ofNullable(metadata.get("ownerReferences"))
        .map(r -> mapper.convertValue(r, new TypeReference<List<OwnerReference>>() {}))
        .orElseGet(ImmutableList::of);
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
  public String getFullResourceName() {
    return getFullResourceName(getKind(), getName());
  }

  public static String getFullResourceName(KubernetesKind kind, String name) {
    return String.join(" ", kind.toString(), name);
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

  /**
   * This method is deprecated in favor of creating a {@link KubernetesCoordinates} object using
   * {@link KubernetesCoordinates.KubernetesCoordinatesBuilder#fullResourceName}, which has more
   * clearly identified named than {@link Pair#getLeft()}) and {@link Pair#getRight()}).
   */
  @Deprecated
  public static Pair<KubernetesKind, String> fromFullResourceName(String fullResourceName) {
    KubernetesCoordinates coords =
        KubernetesCoordinates.builder().fullResourceName(fullResourceName).build();
    return new ImmutablePair<>(coords.getKind(), coords.getName());
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
