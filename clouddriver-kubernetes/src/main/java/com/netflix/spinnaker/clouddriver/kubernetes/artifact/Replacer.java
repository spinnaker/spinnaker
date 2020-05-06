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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.PathNotFoundException;
import com.netflix.spinnaker.clouddriver.artifacts.kubernetes.KubernetesArtifactType;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Builder(access = AccessLevel.PRIVATE)
@ParametersAreNonnullByDefault
@Slf4j
public class Replacer {
  private static final ObjectMapper mapper = new ObjectMapper();

  @Nonnull private final String replacePath;
  @Nonnull private final String findPath;
  @Nullable private final Function<String, String> nameFromReference;
  @Nonnull private final KubernetesArtifactType type;

  private static String substituteField(String result, String fieldName, @Nullable String field) {
    return result.replace("{%" + fieldName + "%}", Optional.ofNullable(field).orElse(""));
  }

  private static String processPath(String path, Artifact artifact) {
    String result = substituteField(path, "name", artifact.getName());
    result = substituteField(result, "type", artifact.getType());
    result = substituteField(result, "version", artifact.getVersion());
    result = substituteField(result, "reference", artifact.getReference());
    return result;
  }

  private ArrayNode findAll(DocumentContext obj) {
    return obj.read(findPath);
  }

  @Nonnull
  private Artifact artifactFromReference(String s) {
    return Artifact.builder().type(type.getType()).reference(s).name(nameFromReference(s)).build();
  }

  @Nonnull
  private String nameFromReference(String s) {
    if (nameFromReference != null) {
      return nameFromReference.apply(s);
    } else {
      return s;
    }
  }

  @Nonnull
  ImmutableCollection<Artifact> getArtifacts(DocumentContext document) {
    return mapper.convertValue(findAll(document), new TypeReference<List<String>>() {}).stream()
        .map(this::artifactFromReference)
        .collect(toImmutableList());
  }

  @Nonnull
  ImmutableCollection<Artifact> replaceArtifacts(
      DocumentContext obj, Collection<Artifact> artifacts) {
    ImmutableSet.Builder<Artifact> replacedArtifacts = new ImmutableSet.Builder<>();
    artifacts.forEach(
        artifact -> {
          boolean wasReplaced = replaceIfPossible(obj, artifact);
          if (wasReplaced) {
            replacedArtifacts.add(artifact);
          }
        });
    return replacedArtifacts.build();
  }

  private boolean replaceIfPossible(DocumentContext obj, @Nullable Artifact artifact) {
    if (artifact == null || Strings.isNullOrEmpty(artifact.getType())) {
      throw new IllegalArgumentException("Artifact and artifact type must be set.");
    }

    if (!artifact.getType().equals(type.getType())) {
      return false;
    }

    String jsonPath = processPath(replacePath, artifact);

    log.debug("Processed jsonPath == {}", jsonPath);

    Object get;
    try {
      get = obj.read(jsonPath);
    } catch (PathNotFoundException e) {
      return false;
    }
    if (get == null || (get instanceof ArrayNode && ((ArrayNode) get).size() == 0)) {
      return false;
    }

    log.info("Found valid swap for " + artifact + " using " + jsonPath + ": " + get);
    obj.set(jsonPath, artifact.getReference());

    return true;
  }

  private static final Replacer DOCKER_IMAGE =
      builder()
          .replacePath(
              "$..spec.template.spec['containers', 'initContainers'].[?( @.image == \"{%name%}\" )].image")
          .findPath("$..spec.template.spec['containers', 'initContainers'].*.image")
          .nameFromReference(
              ref -> {
                int atIndex = ref.indexOf('@');
                // @ can only show up in image references denoting a digest
                // https://github.com/docker/distribution/blob/95daa793b83a21656fe6c13e6d5cf1c3999108c7/reference/regexp.go#L70
                if (atIndex >= 0) {
                  return ref.substring(0, atIndex);
                }

                // : can be used to denote a port, part of a digest (already matched) or a tag
                // https://github.com/docker/distribution/blob/95daa793b83a21656fe6c13e6d5cf1c3999108c7/reference/regexp.go#L69
                int lastColonIndex = ref.lastIndexOf(':');

                if (lastColonIndex < 0) {
                  return ref;
                }

                // we don't need to check if this is a tag, or a port. ports will be matched lazily
                // if
                // they are numeric, and are treated as tags first:
                // https://github.com/docker/distribution/blob/95daa793b83a21656fe6c13e6d5cf1c3999108c7/reference/regexp.go#L34
                return ref.substring(0, lastColonIndex);
              })
          .type(KubernetesArtifactType.DockerImage)
          .build();
  private static final Replacer POD_DOCKER_IMAGE =
      builder()
          .replacePath("$.spec.containers.[?( @.image == \"{%name%}\" )].image")
          .findPath("$.spec.containers.*.image")
          .type(KubernetesArtifactType.DockerImage)
          .build();
  private static final Replacer CONFIG_MAP_VOLUME =
      builder()
          .replacePath(
              "$..spec.template.spec.volumes.[?( @.configMap.name == \"{%name%}\" )].configMap.name")
          .findPath("$..spec.template.spec.volumes.*.configMap.name")
          .type(KubernetesArtifactType.ConfigMap)
          .build();
  private static final Replacer SECRET_VOLUME =
      builder()
          .replacePath(
              "$..spec.template.spec.volumes.[?( @.secret.secretName == \"{%name%}\" )].secret.secretName")
          .findPath("$..spec.template.spec.volumes.*.secret.secretName")
          .type(KubernetesArtifactType.Secret)
          .build();
  private static final Replacer CONFIG_MAP_KEY_VALUE =
      builder()
          .replacePath(
              "$..spec.template.spec['containers', 'initContainers'].*.env.[?( @.valueFrom.configMapKeyRef.name == \"{%name%}\" )].valueFrom.configMapKeyRef.name")
          .findPath(
              "$..spec.template.spec['containers', 'initContainers'].*.env.*.valueFrom.configMapKeyRef.name")
          .type(KubernetesArtifactType.ConfigMap)
          .build();
  private static final Replacer SECRET_KEY_VALUE =
      builder()
          .replacePath(
              "$..spec.template.spec['containers', 'initContainers'].*.env.[?( @.valueFrom.secretKeyRef.name == \"{%name%}\" )].valueFrom.secretKeyRef.name")
          .findPath(
              "$..spec.template.spec['containers', 'initContainers'].*.env.*.valueFrom.secretKeyRef.name")
          .type(KubernetesArtifactType.Secret)
          .build();
  private static final Replacer CONFIG_MAP_ENV =
      builder()
          .replacePath(
              "$..spec.template.spec['containers', 'initContainers'].*.envFrom.[?( @.configMapRef.name == \"{%name%}\" )].configMapRef.name")
          .findPath(
              "$..spec.template.spec['containers', 'initContainers'].*.envFrom.*.configMapRef.name")
          .type(KubernetesArtifactType.ConfigMap)
          .build();
  private static final Replacer SECRET_ENV =
      builder()
          .replacePath(
              "$..spec.template.spec['containers', 'initContainers'].*.envFrom.[?( @.secretRef.name == \"{%name%}\" )].secretRef.name")
          .findPath(
              "$..spec.template.spec['containers', 'initContainers'].*.envFrom.*.secretRef.name")
          .type(KubernetesArtifactType.Secret)
          .build();
  private static final Replacer HPA_DEPLOYMENT =
      builder()
          .replacePath(
              "$[?( (@.spec.scaleTargetRef.kind == \"Deployment\" || @.spec.scaleTargetRef.kind == \"deployment\") && @.spec.scaleTargetRef.name == \"{%name%}\" )].spec.scaleTargetRef.name")
          .findPath(
              "$[?( @.spec.scaleTargetRef.kind == \"Deployment\" || @.spec.scaleTargetRef.kind == \"deployment\" )].spec.scaleTargetRef.name")
          .type(KubernetesArtifactType.Deployment)
          .build();
  private static final Replacer HPA_REPLICA_SET =
      builder()
          .replacePath(
              "$[?( (@.spec.scaleTargetRef.kind == \"ReplicaSet\" || @.spec.scaleTargetRef.kind == \"replicaSet\") && @.spec.scaleTargetRef.name == \"{%name%}\" )].spec.scaleTargetRef.name")
          .findPath(
              "$[?( @.spec.scaleTargetRef.kind == \"ReplicaSet\" || @.spec.scaleTargetRef.kind == \"replicaSet\" )].spec.scaleTargetRef.name")
          .type(KubernetesArtifactType.ReplicaSet)
          .build();

  @Nonnull
  public static Replacer dockerImage() {
    return DOCKER_IMAGE;
  }

  @Nonnull
  public static Replacer podDockerImage() {
    return POD_DOCKER_IMAGE;
  }

  @Nonnull
  public static Replacer configMapVolume() {
    return CONFIG_MAP_VOLUME;
  }

  @Nonnull
  public static Replacer secretVolume() {
    return SECRET_VOLUME;
  }

  @Nonnull
  public static Replacer configMapKeyValue() {
    return CONFIG_MAP_KEY_VALUE;
  }

  @Nonnull
  public static Replacer secretKeyValue() {
    return SECRET_KEY_VALUE;
  }

  @Nonnull
  public static Replacer configMapEnv() {
    return CONFIG_MAP_ENV;
  }

  @Nonnull
  public static Replacer secretEnv() {
    return SECRET_ENV;
  }

  @Nonnull
  public static Replacer hpaDeployment() {
    return HPA_DEPLOYMENT;
  }

  @Nonnull
  public static Replacer hpaReplicaSet() {
    return HPA_REPLICA_SET;
  }
}
