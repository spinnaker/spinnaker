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

package com.netflix.spinnaker.clouddriver.kubernetes.description

import com.google.common.collect.ImmutableSet
import com.netflix.spinnaker.clouddriver.kubernetes.description.SpinnakerKind
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesHandler
import spock.lang.Specification

class KubernetesSpinnakerKindMapSpec extends Specification {
  void "the kind map is properly initialized"() {
    given:
    def mockHandler = Mock(KubernetesHandler) {
      spinnakerKind() >> SpinnakerKind.INSTANCES
      kind() >> KubernetesKind.REPLICA_SET
    }

    when:
    def kindMap = new KubernetesSpinnakerKindMap([mockHandler])

    then:
    kindMap.translateSpinnakerKind(SpinnakerKind.INSTANCES) == ImmutableSet.of(KubernetesKind.REPLICA_SET)
    kindMap.translateKubernetesKind(KubernetesKind.REPLICA_SET) == SpinnakerKind.INSTANCES
  }

  void "the kind map properly groups kinds"() {
    when:
    def kindMap = new KubernetesSpinnakerKindMap([
      Mock(KubernetesHandler) {
        spinnakerKind() >> SpinnakerKind.INSTANCES
        kind() >> KubernetesKind.REPLICA_SET
      },
      Mock(KubernetesHandler) {
        spinnakerKind() >> SpinnakerKind.INSTANCES
        kind() >> KubernetesKind.DEPLOYMENT
      },
      Mock(KubernetesHandler) {
        spinnakerKind() >> SpinnakerKind.LOAD_BALANCERS
        kind() >> KubernetesKind.SERVICE
      }
    ])

    then:
    kindMap.translateSpinnakerKind(SpinnakerKind.INSTANCES) == ImmutableSet.of(KubernetesKind.REPLICA_SET, KubernetesKind.DEPLOYMENT)
    kindMap.translateSpinnakerKind(SpinnakerKind.LOAD_BALANCERS) == ImmutableSet.of(KubernetesKind.SERVICE)
    kindMap.translateKubernetesKind(KubernetesKind.REPLICA_SET) == SpinnakerKind.INSTANCES
    kindMap.translateKubernetesKind(KubernetesKind.DEPLOYMENT) == SpinnakerKind.INSTANCES
    kindMap.translateKubernetesKind(KubernetesKind.SERVICE) == SpinnakerKind.LOAD_BALANCERS
  }
}
