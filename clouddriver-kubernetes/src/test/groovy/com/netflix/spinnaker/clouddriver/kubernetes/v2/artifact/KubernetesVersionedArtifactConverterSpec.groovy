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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.artifact

import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.view.provider.KubernetesV2ArtifactProvider
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiVersion
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import spock.lang.Specification
import spock.lang.Unroll

class KubernetesVersionedArtifactConverterSpec extends Specification {
  @Unroll
  def "correctly infer versioned artifact properties"() {
    expect:
    def type = "kubernetes/$apiVersion|$kind"

    def artifact = Artifact.builder()
      .type(type)
      .name(name)
      .version(version)
      .build()

    def converter = new KubernetesVersionedArtifactConverter()
    converter.getApiVersion(artifact) == apiVersion
    converter.getKind(artifact) == kind
    converter.getDeployedName(artifact) == "$name-$version"


    where:
    apiVersion                              | kind                       | name             | version
    KubernetesApiVersion.EXTENSIONS_V1BETA1 | KubernetesKind.REPLICA_SET | "my-rs"          | "v000"
    KubernetesApiVersion.EXTENSIONS_V1BETA1 | KubernetesKind.REPLICA_SET | "my-other-rs-_-" | "v010"
  }

  @Unroll
  def "correctly pick next version"() {
    when:
    def artifacts = versions.collect { v -> Artifact.builder().version("v$v").build() }
    def artifactProvider = Mock(KubernetesV2ArtifactProvider)
    def type = "type"
    def name = "name"
    def location = "location"

    artifactProvider.getArtifacts(type, name, location) >> artifacts

    def converter = new KubernetesVersionedArtifactConverter()

    then:
    converter.getVersion(artifactProvider, type, name, location) == expected

    where:
    versions  | expected
    [0, 1, 2] | "v003"
    [0]       | "v001"
    []        | "v000"
    [1]       | "v002"
    [1, 2, 3] | "v004"
    [0, 2, 3] | "v004"
    [2, 0, 1] | "v003"
    [0, 1, 3] | "v004"
    [1, 0, 3] | "v004"
    [1000]    | "v1001"
  }
}
