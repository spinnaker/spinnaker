/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.op.handler

import spock.lang.Specification
import spock.lang.Unroll

class HandlerPrioritySpec extends Specification {
  @Unroll
  void "check that #a is deployed before (has lower priority rating) #b"() {
    when:
    def aPriority = a.deployPriority()
    def bPriority = b.deployPriority()

    then:
    aPriority < bPriority

    where:
    a                                    | b
    new KubernetesNetworkPolicyHandler() | new KubernetesPodHandler()
    new KubernetesNetworkPolicyHandler() | new KubernetesDeploymentHandler()
    new KubernetesNetworkPolicyHandler() | new KubernetesStatefulSetHandler()
    new KubernetesNetworkPolicyHandler() | new KubernetesDaemonSetHandler()
    new KubernetesNetworkPolicyHandler() | new KubernetesReplicaSetHandler()
    new KubernetesNamespaceHandler()     | new KubernetesNetworkPolicyHandler()
    new KubernetesNamespaceHandler()     | new KubernetesRoleBindingHandler()
    new KubernetesNamespaceHandler()     | new KubernetesRoleHandler()
    new KubernetesNamespaceHandler()     | new KubernetesIngressHandler()
    new KubernetesServiceHandler()       | new KubernetesPodHandler()
    new KubernetesServiceHandler()       | new KubernetesDeploymentHandler()
    new KubernetesConfigMapHandler()     | new KubernetesPodHandler()
    new KubernetesConfigMapHandler()     | new KubernetesDeploymentHandler()
    new KubernetesConfigMapHandler()     | new KubernetesStatefulSetHandler()
    new KubernetesConfigMapHandler()     | new KubernetesDaemonSetHandler()
    new KubernetesConfigMapHandler()     | new KubernetesReplicaSetHandler()
  }
}
