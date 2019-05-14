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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.frigga.Names;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.moniker.Moniker;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class KubernetesManifestAnnotater {
  private static final String SPINNAKER_ANNOTATION = "spinnaker.io";
  private static final String TRAFFIC_ANNOTATION_PREFIX = "traffic." + SPINNAKER_ANNOTATION;
  private static final String ARTIFACT_ANNOTATION_PREFIX = "artifact." + SPINNAKER_ANNOTATION;
  private static final String MONIKER_ANNOTATION_PREFIX = "moniker." + SPINNAKER_ANNOTATION;
  private static final String CACHING_ANNOTATION_PREFIX = "caching." + SPINNAKER_ANNOTATION;
  private static final String STRATEGY_ANNOTATION_PREFIX = "strategy." + SPINNAKER_ANNOTATION;
  private static final String CLUSTER = MONIKER_ANNOTATION_PREFIX + "/cluster";
  private static final String APPLICATION = MONIKER_ANNOTATION_PREFIX + "/application";
  private static final String STACK = MONIKER_ANNOTATION_PREFIX + "/stack";
  private static final String DETAIL = MONIKER_ANNOTATION_PREFIX + "/detail";
  private static final String SEQUENCE = MONIKER_ANNOTATION_PREFIX + "/sequence";
  private static final String TYPE = ARTIFACT_ANNOTATION_PREFIX + "/type";
  private static final String NAME = ARTIFACT_ANNOTATION_PREFIX + "/name";
  private static final String LOCATION = ARTIFACT_ANNOTATION_PREFIX + "/location";
  private static final String VERSION = ARTIFACT_ANNOTATION_PREFIX + "/version";
  private static final String IGNORE_CACHING = CACHING_ANNOTATION_PREFIX + "/ignore";
  private static final String VERSIONED = STRATEGY_ANNOTATION_PREFIX + "/versioned";
  private static final String RECREATE = STRATEGY_ANNOTATION_PREFIX + "/recreate";
  private static final String REPLACE = STRATEGY_ANNOTATION_PREFIX + "/replace";
  private static final String MAX_VERSION_HISTORY =
      STRATEGY_ANNOTATION_PREFIX + "/max-version-history";
  private static final String USE_SOURCE_CAPACITY =
      STRATEGY_ANNOTATION_PREFIX + "/use-source-capacity";
  private static final String LOAD_BALANCERS = TRAFFIC_ANNOTATION_PREFIX + "/load-balancers";

  private static final String KUBERNETES_ANNOTATION = "kubernetes.io";
  private static final String KUBECTL_ANNOTATION_PREFIX = "kubectl." + KUBERNETES_ANNOTATION;
  private static final String DEPLOYMENT_ANNOTATION_PREFIX = "deployment." + KUBERNETES_ANNOTATION;
  private static final String DEPLOYMENT_REVISION = DEPLOYMENT_ANNOTATION_PREFIX + "/revision";
  private static final String KUBECTL_LAST_APPLIED_CONFIGURATION =
      KUBECTL_ANNOTATION_PREFIX + "/last-applied-configuration";

  private static ObjectMapper objectMapper = new ObjectMapper();

  private static void storeAnnotation(Map<String, String> annotations, String key, Object value) {
    if (value == null) {
      return;
    }

    if (annotations.containsKey(key)) {
      return;
    }

    try {
      if (value instanceof String) {
        // The "write value as string" method will attach quotes which are ugly to read
        annotations.put(key, (String) value);
      } else {
        annotations.put(key, objectMapper.writeValueAsString(value));
      }
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Illegal annotation value for '" + key + "': " + e);
    }
  }

  private static <T> T getAnnotation(
      Map<String, String> annotations, String key, TypeReference<T> typeReference) {
    return getAnnotation(annotations, key, typeReference, null);
  }

  private static boolean stringTypeReference(TypeReference typeReference) {
    if (typeReference.getType() == null || typeReference.getType().getTypeName() == null) {
      log.warn("Malformed type reference {}", typeReference);
      return false;
    }

    return typeReference.getType().getTypeName().equals(String.class.getName());
  }

  // This is to read values that were annotated with the ObjectMapper with quotes, before we started
  // ignoring the quotes
  private static boolean looksLikeSerializedString(String value) {
    if (StringUtils.isEmpty(value) || value.length() == 1) {
      return false;
    }

    return value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"';
  }

  private static <T> T getAnnotation(
      Map<String, String> annotations, String key, TypeReference<T> typeReference, T defaultValue) {
    String value = annotations.get(key);
    if (value == null) {
      return defaultValue;
    }

    try {
      boolean wantsString = stringTypeReference(typeReference);

      if (wantsString && !looksLikeSerializedString(value)) {
        return (T) value;
      } else {
        return objectMapper.readValue(value, typeReference);
      }
    } catch (Exception e) {
      log.warn("Illegally annotated resource for '" + key + "': " + e);
      return null;
    }
  }

  public static void annotateManifest(KubernetesManifest manifest, Moniker moniker) {
    Map<String, String> annotations = manifest.getAnnotations();
    storeAnnotations(annotations, moniker);

    manifest
        .getSpecTemplateAnnotations()
        .flatMap(
            a -> {
              storeAnnotations(a, moniker);
              return Optional.empty();
            });
  }

  public static void annotateManifest(KubernetesManifest manifest, Artifact artifact) {
    Map<String, String> annotations = manifest.getAnnotations();
    storeAnnotations(annotations, artifact);

    manifest
        .getSpecTemplateAnnotations()
        .flatMap(
            a -> {
              storeAnnotations(a, artifact);
              return Optional.empty();
            });
  }

  private static void storeAnnotations(Map<String, String> annotations, Moniker moniker) {
    if (moniker == null) {
      throw new IllegalArgumentException(
          "Every resource deployed via spinnaker must be assigned a moniker");
    }

    storeAnnotation(annotations, CLUSTER, moniker.getCluster());
    storeAnnotation(annotations, APPLICATION, moniker.getApp());
    storeAnnotation(annotations, STACK, moniker.getStack());
    storeAnnotation(annotations, DETAIL, moniker.getDetail());
    storeAnnotation(annotations, SEQUENCE, moniker.getSequence());
  }

  private static void storeAnnotations(Map<String, String> annotations, Artifact artifact) {
    if (artifact == null) {
      return;
    }

    storeAnnotation(annotations, TYPE, artifact.getType());
    storeAnnotation(annotations, NAME, artifact.getName());
    storeAnnotation(annotations, LOCATION, artifact.getLocation());
    storeAnnotation(annotations, VERSION, artifact.getVersion());
  }

  public static Optional<Artifact> getArtifact(KubernetesManifest manifest) {
    Map<String, String> annotations = manifest.getAnnotations();
    String type = getAnnotation(annotations, TYPE, new TypeReference<String>() {});
    if (StringUtils.isEmpty(type)) {
      return Optional.empty();
    }

    return Optional.of(
        Artifact.builder()
            .type(type)
            .name(getAnnotation(annotations, NAME, new TypeReference<String>() {}))
            .location(getAnnotation(annotations, LOCATION, new TypeReference<String>() {}))
            .version(getAnnotation(annotations, VERSION, new TypeReference<String>() {}))
            .build());
  }

  public static Moniker getMoniker(KubernetesManifest manifest) {
    Names parsed = Names.parseName(manifest.getName());
    Map<String, String> annotations = manifest.getAnnotations();
    Integer defaultSequence = parsed.getSequence();

    return Moniker.builder()
        .cluster(
            getAnnotation(
                annotations, CLUSTER, new TypeReference<String>() {}, parsed.getCluster()))
        .app(
            getAnnotation(
                annotations, APPLICATION, new TypeReference<String>() {}, parsed.getApp()))
        .stack(getAnnotation(annotations, STACK, new TypeReference<String>() {}, null))
        .detail(getAnnotation(annotations, DETAIL, new TypeReference<String>() {}, null))
        .sequence(
            getAnnotation(
                annotations,
                SEQUENCE,
                new TypeReference<Integer>() {},
                manifest.getKind() == KubernetesKind.REPLICA_SET
                    ? getAnnotation(
                        annotations,
                        DEPLOYMENT_REVISION,
                        new TypeReference<Integer>() {},
                        defaultSequence)
                    : defaultSequence))
        .build();
  }

  public static KubernetesManifestTraffic getTraffic(KubernetesManifest manifest) {
    Map<String, String> annotations = manifest.getAnnotations();

    List<String> loadBalancers =
        getAnnotation(
            annotations, LOAD_BALANCERS, new TypeReference<List<String>>() {}, new ArrayList<>());
    return new KubernetesManifestTraffic(loadBalancers);
  }

  public static void setTraffic(KubernetesManifest manifest, KubernetesManifestTraffic traffic) {
    Map<String, String> annotations = manifest.getAnnotations();
    List<String> loadBalancers = traffic.getLoadBalancers();

    if (annotations.containsKey(LOAD_BALANCERS)) {
      KubernetesManifestTraffic currentTraffic = getTraffic(manifest);
      if (currentTraffic.getLoadBalancers().equals(loadBalancers)) {
        return;
      } else {
        throw new RuntimeException(
            String.format(
                "Manifest already has %s annotation set to %s. Failed attempting to set it to %s.",
                LOAD_BALANCERS, currentTraffic.getLoadBalancers(), loadBalancers));
      }
    }
    storeAnnotation(annotations, LOAD_BALANCERS, loadBalancers);
  }

  public static void validateAnnotationsForRolloutStrategies(
      KubernetesManifest manifest, KubernetesDeployManifestDescription.Strategy strategy) {
    Map<String, String> annotations = manifest.getAnnotations();
    Integer maxVersionHistory =
        getAnnotation(annotations, MAX_VERSION_HISTORY, new TypeReference<Integer>() {});
    if (strategy == KubernetesDeployManifestDescription.Strategy.RED_BLACK
        && maxVersionHistory != null
        && maxVersionHistory < 2) {
      throw new RuntimeException(
          String.format(
              "The max version history specified in your manifest conflicts with the behavior of the Red/Black rollout strategy. Please update your %s annotation to a value greater than or equal to 2.",
              MAX_VERSION_HISTORY));
    }
  }

  public static KubernetesCachingProperties getCachingProperties(KubernetesManifest manifest) {
    Map<String, String> annotations = manifest.getAnnotations();

    return KubernetesCachingProperties.builder()
        .ignore(getAnnotation(annotations, IGNORE_CACHING, new TypeReference<Boolean>() {}, false))
        .application(getAnnotation(annotations, APPLICATION, new TypeReference<String>() {}, ""))
        .build();
  }

  public static KubernetesManifestStrategy getStrategy(KubernetesManifest manifest) {
    Map<String, String> annotations = manifest.getAnnotations();

    return KubernetesManifestStrategy.builder()
        .versioned(getAnnotation(annotations, VERSIONED, new TypeReference<Boolean>() {}))
        .recreate(getAnnotation(annotations, RECREATE, new TypeReference<Boolean>() {}))
        .replace(getAnnotation(annotations, REPLACE, new TypeReference<Boolean>() {}))
        .maxVersionHistory(
            getAnnotation(annotations, MAX_VERSION_HISTORY, new TypeReference<Integer>() {}))
        .useSourceCapacity(
            getAnnotation(annotations, USE_SOURCE_CAPACITY, new TypeReference<Boolean>() {}))
        .build();
  }

  public static KubernetesManifest getLastAppliedConfiguration(KubernetesManifest manifest) {
    Map<String, String> annotations = manifest.getAnnotations();

    return getAnnotation(
        annotations,
        KUBECTL_LAST_APPLIED_CONFIGURATION,
        new TypeReference<KubernetesManifest>() {},
        null);
  }
}
