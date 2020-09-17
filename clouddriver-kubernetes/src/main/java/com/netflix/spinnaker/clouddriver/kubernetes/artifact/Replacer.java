/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.artifact;

import static com.jayway.jsonpath.Criteria.where;
import static com.jayway.jsonpath.Filter.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.Filter;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.Predicate;
import com.jayway.jsonpath.internal.filter.ValueNode;
import com.netflix.spinnaker.clouddriver.artifacts.kubernetes.KubernetesArtifactType;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import lombok.AccessLevel;
import lombok.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NonnullByDefault
public final class Replacer {
  private static final Logger log = LoggerFactory.getLogger(Replacer.class);

  private final KubernetesArtifactType type;
  private final JsonPath findPath;
  private final Function<Artifact, JsonPath> replacePathSupplier;
  private final Function<String, String> nameFromReference;

  /**
   * @param type the type of artifact this replacer handles
   * @param path a string representing a JsonPath expression containing a single [?] placeholder
   *     representing a filter
   * @param findFilter a filter that should be applied to the path when finding any artifacts in a
   *     manifest; defaults to a filter matching all nodes
   * @param replacePathFromPlaceholder a string that represents the path from the [?] placeholder to
   *     the replaced field.
   * @param nameFromReference a function to extract an artifact name from its reference; defaults to
   *     returning the reference
   */
  @Builder(access = AccessLevel.PRIVATE)
  private Replacer(
      KubernetesArtifactType type,
      String path,
      @Nullable Filter findFilter,
      String replacePathFromPlaceholder,
      @Nullable Function<String, String> nameFromReference) {
    this.type = Objects.requireNonNull(type);
    Objects.requireNonNull(path);
    Objects.requireNonNull(replacePathFromPlaceholder);
    this.nameFromReference = Optional.ofNullable(nameFromReference).orElse(a -> a);
    Function<Artifact, Filter> replaceFilter =
        a -> filter(createReplaceFilterPredicate(replacePathFromPlaceholder, a.getName()));
    if (findFilter != null) {
      this.findPath = JsonPath.compile(path, findFilter);
      this.replacePathSupplier =
          a -> JsonPath.compile(path, replaceFilter.apply(a).and(findFilter));
    } else {
      this.findPath = JsonPath.compile(path, filter(a -> true));
      this.replacePathSupplier = a -> JsonPath.compile(path, replaceFilter.apply(a));
    }
  }

  Stream<Artifact> getArtifacts(DocumentContext document) {
    return Streams.stream(document.<ArrayNode>read(findPath).elements())
        .map(JsonNode::asText)
        .map(
            ref ->
                Artifact.builder()
                    .type(type.getType())
                    .reference(ref)
                    .name(nameFromReference.apply(ref))
                    .build());
  }

  ImmutableCollection<Artifact> replaceArtifacts(
      DocumentContext obj, Collection<Artifact> artifacts) {
    ImmutableSet.Builder<Artifact> replacedArtifacts = ImmutableSet.builder();
    for (Artifact artifact : artifacts) {
      boolean wasReplaced = replaceIfPossible(obj, artifact);
      if (wasReplaced) {
        replacedArtifacts.add(artifact);
      }
    }
    return replacedArtifacts.build();
  }

  private Predicate createReplaceFilterPredicate(String replacePath, String name) {
    return ctx -> {
      ValueNode node = ValueNode.toValueNode("@." + replacePath).asPathNode().evaluate(ctx);
      if (!node.isStringNode()) {
        return false;
      }
      String value = node.asStringNode().getString();
      return nameFromReference.apply(value).equals(name);
    };
  }

  private boolean replaceIfPossible(DocumentContext obj, Artifact artifact) {
    if (!type.getType().equals(artifact.getType())) {
      return false;
    }

    JsonPath path = replacePathSupplier.apply(artifact);
    log.debug("Processed jsonPath == {}", path.getPath());

    Object get;
    try {
      get = obj.read(path);
    } catch (PathNotFoundException e) {
      return false;
    }
    if (get == null || (get instanceof ArrayNode && ((ArrayNode) get).size() == 0)) {
      return false;
    }

    log.info("Found valid swap for " + artifact + " using " + path.getPath() + ": " + get);
    obj.set(path, artifact.getReference());

    return true;
  }

  private static final Replacer DOCKER_IMAGE =
      builder()
          .path("$..spec.template.spec['containers', 'initContainers'].[?].image")
          .replacePathFromPlaceholder("image")
          .nameFromReference(
              ref -> {
                // @ can only show up in image references denoting a digest
                // https://github.com/docker/distribution/blob/95daa793b83a21656fe6c13e6d5cf1c3999108c7/reference/regexp.go#L70
                int atIndex = ref.indexOf('@');
                if (atIndex >= 0) {
                  return ref.substring(0, atIndex);
                }

                // : can be used to denote a port, part of a digest (already matched) or a tag
                // https://github.com/docker/distribution/blob/95daa793b83a21656fe6c13e6d5cf1c3999108c7/reference/regexp.go#L69
                int lastColonIndex = ref.lastIndexOf(':');
                if (lastColonIndex >= 0) {
                  // we don't need to check if this is a tag, or a port. ports will be matched
                  // lazily if they are numeric, and are treated as tags first:
                  // https://github.com/docker/distribution/blob/95daa793b83a21656fe6c13e6d5cf1c3999108c7/reference/regexp.go#L34
                  return ref.substring(0, lastColonIndex);
                }
                return ref;
              })
          .type(KubernetesArtifactType.DockerImage)
          .build();
  private static final Replacer POD_DOCKER_IMAGE =
      builder()
          .path("$.spec.containers.[?].image")
          .replacePathFromPlaceholder("image")
          .type(KubernetesArtifactType.DockerImage)
          .build();
  private static final Replacer CONFIG_MAP_VOLUME =
      builder()
          .path("$..spec.template.spec.volumes.[?].configMap.name")
          .replacePathFromPlaceholder("configMap.name")
          .type(KubernetesArtifactType.ConfigMap)
          .build();
  private static final Replacer SECRET_VOLUME =
      builder()
          .path("$..spec.template.spec.volumes.[?].secret.secretName")
          .replacePathFromPlaceholder("secret.secretName")
          .type(KubernetesArtifactType.Secret)
          .build();
  private static final Replacer CONFIG_MAP_KEY_VALUE =
      builder()
          .path(
              "$..spec.template.spec['containers', 'initContainers'].*.env.[?].valueFrom.configMapKeyRef.name")
          .replacePathFromPlaceholder("valueFrom.configMapKeyRef.name")
          .type(KubernetesArtifactType.ConfigMap)
          .build();
  private static final Replacer SECRET_KEY_VALUE =
      builder()
          .path(
              "$..spec.template.spec['containers', 'initContainers'].*.env.[?].valueFrom.secretKeyRef.name")
          .replacePathFromPlaceholder("valueFrom.secretKeyRef.name")
          .type(KubernetesArtifactType.Secret)
          .build();
  private static final Replacer CONFIG_MAP_ENV =
      builder()
          .path(
              "$..spec.template.spec['containers', 'initContainers'].*.envFrom.[?].configMapRef.name")
          .replacePathFromPlaceholder("configMapRef.name")
          .type(KubernetesArtifactType.ConfigMap)
          .build();
  private static final Replacer SECRET_ENV =
      builder()
          .path(
              "$..spec.template.spec['containers', 'initContainers'].*.envFrom.[?].secretRef.name")
          .replacePathFromPlaceholder("secretRef.name")
          .type(KubernetesArtifactType.Secret)
          .build();
  private static final Replacer HPA_DEPLOYMENT =
      builder()
          .path("$[?].spec.scaleTargetRef.name")
          .findFilter(
              filter(where("spec.scaleTargetRef.kind").is("Deployment"))
                  .or(where("spec.scaleTargetRef.kind").is("deployment")))
          .replacePathFromPlaceholder("spec.scaleTargetRef.name")
          .type(KubernetesArtifactType.Deployment)
          .build();
  private static final Replacer HPA_REPLICA_SET =
      builder()
          .path("$[?].spec.scaleTargetRef.name")
          .findFilter(
              filter(where("spec.scaleTargetRef.kind").is("ReplicaSet"))
                  .or(where("spec.scaleTargetRef.kind").is("replicaSet")))
          .replacePathFromPlaceholder("spec.scaleTargetRef.name")
          .type(KubernetesArtifactType.ReplicaSet)
          .build();

  public static Replacer dockerImage() {
    return DOCKER_IMAGE;
  }

  public static Replacer podDockerImage() {
    return POD_DOCKER_IMAGE;
  }

  public static Replacer configMapVolume() {
    return CONFIG_MAP_VOLUME;
  }

  public static Replacer secretVolume() {
    return SECRET_VOLUME;
  }

  public static Replacer configMapKeyValue() {
    return CONFIG_MAP_KEY_VALUE;
  }

  public static Replacer secretKeyValue() {
    return SECRET_KEY_VALUE;
  }

  public static Replacer configMapEnv() {
    return CONFIG_MAP_ENV;
  }

  public static Replacer secretEnv() {
    return SECRET_ENV;
  }

  public static Replacer hpaDeployment() {
    return HPA_DEPLOYMENT;
  }

  public static Replacer hpaReplicaSet() {
    return HPA_REPLICA_SET;
  }
}
