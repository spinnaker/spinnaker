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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.description

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiVersion
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import spock.lang.Specification
import spock.lang.Unroll

class KubernetesManifestSpec extends Specification {
  def objectMapper = new ObjectMapper()
  def yaml = new Yaml(new SafeConstructor())

  def NAME = "my-name"
  def NAMESPACE = "my-namespace"
  def KIND = KubernetesKind.REPLICA_SET
  def API_VERSION = KubernetesApiVersion.EXTENSIONS_V1BETA1
  def KEY = "hi"
  def VALUE = "there"

  def BASIC_REPLICA_SET = """
apiVersion: $API_VERSION
kind: $KIND
metadata:
  name: $NAME
  namespace: $NAMESPACE
spec:
  template:
    metadata:
      annotations:
        $KEY: $VALUE 
"""

  KubernetesManifest stringToManifest(String input) {
    return objectMapper.convertValue(yaml.load(input), KubernetesManifest)
  }

  void "correctly reads fields from basic manifest definition"() {
    when:
    KubernetesManifest manifest = stringToManifest(BASIC_REPLICA_SET)

    then:
    manifest.getName() == NAME
    manifest.getNamespace() == NAMESPACE
    manifest.getKind() == KIND
    manifest.getApiVersion() == API_VERSION
    manifest.getSpecTemplateAnnotations().get().get(KEY) == VALUE
  }

  @Unroll
  void "correctly parses a fully qualified resource name #kind/#name"() {
    expect:
    def pair = KubernetesManifest.fromFullResourceName(fullResourceName)
    pair.getRight() == name
    pair.getLeft() == kind

    where:
    fullResourceName || kind                       | name
    "replicaSet abc" || KubernetesKind.REPLICA_SET | "abc"
    "rs abc"         || KubernetesKind.REPLICA_SET | "abc"
    "service abc"    || KubernetesKind.SERVICE     | "abc"
    "SERVICE abc"    || KubernetesKind.SERVICE     | "abc"
    "ingress abc"    || KubernetesKind.INGRESS     | "abc"
  }
}
