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

package com.netflix.spinnaker.clouddriver.kubernetes.artifact

import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesApiVersion
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import spock.lang.Specification
import spock.lang.Unroll

class KubernetesUnversionedArtifactConverterSpec extends Specification {
  @Unroll
  def "correctly infer unversioned artifact properties"() {
    expect:
    def type = "kubernetes/$kind"

    def artifact = Artifact.builder()
      .type(type)
      .name(name)
      .build()

    def converter = KubernetesUnversionedArtifactConverter.INSTANCE
    converter.getDeployedName(artifact) == "$name"

    where:
    apiVersion                        | kind                      | name
    KubernetesApiVersion.APPS_V1BETA1 | KubernetesKind.DEPLOYMENT | "my-deploy"
    KubernetesApiVersion.V1           | KubernetesKind.SERVICE    | "my-other-rs-_-"
  }
}
