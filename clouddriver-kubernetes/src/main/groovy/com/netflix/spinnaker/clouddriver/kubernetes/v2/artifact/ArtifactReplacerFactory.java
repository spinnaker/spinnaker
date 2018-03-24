/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact;

import com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact.ArtifactReplacer.Replacer;

import java.util.regex.Pattern;

public class ArtifactReplacerFactory {
  // The following was derived from
  // https://github.com/docker/distribution/blob/95daa793b83a21656fe6c13e6d5cf1c3999108c7/reference/regexp.go
  private final static String DOCKER_NAME_COMPONENT = "[a-z0-9]+(?:(?:(?:[._]|__|[-]*)[a-z0-9]+)+)?";
  private final static String DOCKER_OPTIONAL_TAG = "(?::[\\w][\\w.-]{0,127})?";
  private final static String DOCKER_OPTIONAL_DIGEST = "(?:@[A-Za-z][A-Za-z0-9]*(?:[-_+.][A-Za-z][A-Za-z0-9]*)*[:][0-9A-Fa-f]{32,})?";
  private final static String DOCKER_DOMAIN = "(?:[a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9])(?:(?:\\.(?:[a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9]))+)?";
  private final static String DOCKER_OPTIONAL_PORT = "(?::[0-9]+)?";
  private final static String DOCKER_OPTIONAL_DOMAIN_AND_PORT = "(?:" + DOCKER_DOMAIN + DOCKER_OPTIONAL_PORT + "/)?";
  private final static String DOCKER_IMAGE_NAME = "(" + DOCKER_OPTIONAL_DOMAIN_AND_PORT + DOCKER_NAME_COMPONENT + "(?:/" + DOCKER_NAME_COMPONENT + ")*)";
  private final static String DOCKER_IMAGE_REFERENCE = DOCKER_IMAGE_NAME + "(" + DOCKER_OPTIONAL_TAG + "|"+ DOCKER_OPTIONAL_DIGEST + ")";

  // the image reference pattern has two capture groups.
  // - the first captures the image name
  // - the second captures the image tag (including the leading ":") or digest (including the leading "@").
  public static final Pattern DOCKER_IMAGE_REFERENCE_PATTERN = Pattern.compile("^" + DOCKER_IMAGE_REFERENCE + "$");

  public static Replacer dockerImageReplacer() {
    return Replacer.builder()
        .replacePath("$.spec.template.spec.containers.[?( @.image == \"{%name%}\" )].image")
        .findPath("$.spec.template.spec.containers.*.image")
        .namePattern(DOCKER_IMAGE_REFERENCE_PATTERN)
        .type(ArtifactTypes.DOCKER_IMAGE)
        .build();
  }

  public static Replacer configMapVolumeReplacer() {
    return Replacer.builder()
        .replacePath("$.spec.template.spec.volumes.[?( @.configMap.name == \"{%name%}\" )].configMap.name")
        .findPath("$.spec.template.spec.volumes.*.configMap.name")
        .type(ArtifactTypes.KUBERNETES_CONFIG_MAP)
        .build();
  }

  public static Replacer secretVolumeReplacer() {
    return Replacer.builder()
        .replacePath("$.spec.template.spec.volumes.[?( @.secret.name == \"{%name%}\" )].secret.name")
        .findPath("$.spec.template.spec.volumes.*.secret.name")
        .type(ArtifactTypes.KUBERNETES_SECRET)
        .build();
  }

  public static Replacer configMapEnvFromReplacer() {
    return Replacer.builder()
        .replacePath("$.spec.template.spec.containers.*.envFrom.[?( @.configMapRef.name == \"{%name%}\" )].configMapRef.name")
        .findPath("$.spec.template.spec.containers.*.envFrom.*.configMapRef.name")
        .type(ArtifactTypes.KUBERNETES_CONFIG_MAP)
        .build();
  }

  public static Replacer secretEnvFromReplacer() {
    return Replacer.builder()
        .replacePath("$.spec.template.spec.containers.*.envFrom.[?( @.secretRef.name == \"{%name%}\" )].secretRef.name")
        .findPath("$.spec.template.spec.containers.*.envFrom.*.secretRef.name")
        .type(ArtifactTypes.KUBERNETES_SECRET)
        .build();
  }
}
