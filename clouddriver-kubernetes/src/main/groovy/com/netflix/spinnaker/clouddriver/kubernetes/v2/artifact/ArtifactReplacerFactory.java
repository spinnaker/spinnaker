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
  public static Replacer dockerImageReplacer() {
    return Replacer.builder()
        .replacePath("$.spec.template.spec.containers.[?( @.image == \"{%name%}\" )].image")
        .findPath("$.spec.template.spec.containers.*.image")
        .namePattern(Pattern.compile("([0-9A-Za-z./]+).*"))
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
