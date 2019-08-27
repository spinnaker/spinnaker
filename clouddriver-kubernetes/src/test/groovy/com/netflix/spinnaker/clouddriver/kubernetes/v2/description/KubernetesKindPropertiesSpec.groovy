/*
 * Copyright 2019 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.description


import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKindProperties
import spock.lang.Specification

class KubernetesKindPropertiesSpec extends Specification {
  def "creates and returns the supplied properties"() {
    when:
    def properties = new KubernetesKindProperties(KubernetesKind.REPLICA_SET, true, true, true)

    then:
    properties.getKubernetesKind() == KubernetesKind.REPLICA_SET
    properties.isNamespaced()
    properties.hasClusterRelationship()
    properties.isDynamic()

    when:
    properties = new KubernetesKindProperties(KubernetesKind.REPLICA_SET, false, false, false)

    then:
    properties.getKubernetesKind() == KubernetesKind.REPLICA_SET
    !properties.isNamespaced()
    !properties.hasClusterRelationship()
    !properties.isDynamic()
  }

  def "sets default properties to the expected values"() {
    when:
    def properties = KubernetesKindProperties.withDefaultProperties(KubernetesKind.REPLICA_SET)

    then:
    properties.isNamespaced()
    !properties.hasClusterRelationship()
    properties.isDynamic()
  }

  def "returns expected results for built-in kinds"() {
    when:
    def defaultProperties = KubernetesKindProperties.getGlobalKindProperties()

    then:
    defaultProperties.contains(new KubernetesKindProperties(KubernetesKind.REPLICA_SET, true, true, false))
    defaultProperties.contains(new KubernetesKindProperties(KubernetesKind.NAMESPACE, false, false, false))
  }
}
