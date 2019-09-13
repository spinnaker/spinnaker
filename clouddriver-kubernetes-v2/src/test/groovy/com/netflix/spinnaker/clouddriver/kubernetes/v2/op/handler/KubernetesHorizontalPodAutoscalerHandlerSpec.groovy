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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.artifacts.kubernetes.KubernetesArtifactType
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class KubernetesHorizontalPodAutoscalerHandlerSpec extends Specification {
  def objectMapper = new ObjectMapper()
  def yaml = new Yaml(new SafeConstructor())
  def handler = new KubernetesHorizontalPodAutoscalerHandler()
  def ACCOUNT = "my-account"
  @Shared
  def namespace = "default"

  def BASIC_HPA = """
apiVersion: autoscaling/v2beta1
kind: HorizontalPodAutoscaler
metadata:
  name: my-hpa
  namespace: $namespace
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: %s
    name: %s
"""

  KubernetesManifest stringToManifest(String input) {
    return objectMapper.convertValue(yaml.load(input), KubernetesManifest)
  }

  @Unroll
  void "check that the #kind #name is replaced by the artifact replacer"() {
    expect:
    def artifact = Artifact.builder()
        .type(type.type)
        .name(name)
        .reference(reference)
        .location(namespace)
        .build()

    def result = handler.replaceArtifacts(stringToManifest(String.format(BASIC_HPA, kind, name)), [artifact], ACCOUNT)

    result.manifest.spec.scaleTargetRef.name == reference
    result.boundArtifacts.size() == 1
    result.boundArtifacts.contains(artifact)

    where:
    kind         | name  | reference  | type
    "deployment" | "abc" | "abc-v000" | KubernetesArtifactType.Deployment
    "Deployment" | "abc" | "abc-v000" | KubernetesArtifactType.Deployment
    "replicaSet" | "xyz" | "xyz-v000" | KubernetesArtifactType.ReplicaSet
    "ReplicaSet" | "xyz" | "xyz-v000" | KubernetesArtifactType.ReplicaSet
  }

  @Unroll
  void "check that the #kind #name is not replaced by the artifact replacer"() {
    expect:
    def artifact = Artifact.builder()
        .type(type.toString())
        .name(name)
        .reference(name)
        .location(location)
        .build()

    def result = handler.replaceArtifacts(stringToManifest(String.format(BASIC_HPA, kind, name)), [artifact], ACCOUNT)

    result.boundArtifacts.size() == 0

    where:
    kind         | name  | location      | type
    "deployment" | "abc" | namespace     | KubernetesArtifactType.ReplicaSet
    "Deployment" | "abc" | "$namespace-" | KubernetesArtifactType.Deployment
  }
}
