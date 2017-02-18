/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.model

import com.netflix.spinnaker.clouddriver.kubernetes.deploy.KubernetesUtil
import com.netflix.spinnaker.clouddriver.model.HealthState
import io.fabric8.kubernetes.api.model.ReplicationController
import spock.lang.Specification

class KubernetesServerGroupSpec extends Specification {
  final private String ACCOUNT = "account"
  KubernetesInstance upInstanceMock
  KubernetesInstance downInstanceMock
  KubernetesInstance startingInstanceMock
  KubernetesInstance unknownInstanceMock
  KubernetesInstance outOfServiceInstanceMock

  def setup() {
    upInstanceMock = Mock(KubernetesInstance)
    downInstanceMock = Mock(KubernetesInstance)
    startingInstanceMock = Mock(KubernetesInstance)
    unknownInstanceMock = Mock(KubernetesInstance)
    outOfServiceInstanceMock = Mock(KubernetesInstance)

    upInstanceMock.getHealthState() >> HealthState.Up
    downInstanceMock.getHealthState() >> HealthState.Down
    startingInstanceMock.getHealthState() >> HealthState.Starting
    unknownInstanceMock.getHealthState() >> HealthState.Unknown
    outOfServiceInstanceMock.getHealthState() >> HealthState.OutOfService
  }

  void "Should return 1 up instances"() {
    when:
      def serverGroup = new KubernetesServerGroup(new ReplicationController(), ACCOUNT, [], null)
      serverGroup.instances = [upInstanceMock] as Set

    then:
      serverGroup.instanceCounts.up == 1
      serverGroup.instanceCounts.down == 0
      serverGroup.instanceCounts.unknown == 0
      serverGroup.instanceCounts.outOfService == 0
      serverGroup.instanceCounts.starting == 0
  }

  void "Should return 1 up, 1 down, 1 starting, 1 oos, 1 unknown instances"() {
    when:
      def serverGroup = new KubernetesServerGroup(new ReplicationController(), ACCOUNT, [], null)
      serverGroup.instances = [upInstanceMock, downInstanceMock, startingInstanceMock, unknownInstanceMock, outOfServiceInstanceMock] as Set

    then:
      serverGroup.instanceCounts.up == 1
      serverGroup.instanceCounts.down == 1
      serverGroup.instanceCounts.unknown == 1
      serverGroup.instanceCounts.outOfService == 1
      serverGroup.instanceCounts.starting == 1
  }

  void "Should list servergroup with no load balancers as enabled"() {
    when:
      def serverGroup = new KubernetesServerGroup(new ReplicationController(), ACCOUNT, [], null)
      serverGroup.instances = [] as Set
      serverGroup.replicas = 1
      serverGroup.labels = ["hi": "there"]

    then:
      !serverGroup.isDisabled()
  }

  void "Should list servergroup with no enabled load balancers as disabled"() {
    when:
      def serverGroup = new KubernetesServerGroup(new ReplicationController(), ACCOUNT, [], null)
      serverGroup.instances = [] as Set
      serverGroup.replicas = 1
      serverGroup.labels = [(KubernetesUtil.loadBalancerKey("1")): "false"]

    then:
      serverGroup.isDisabled()
  }

  void "Should list servergroup with enabled load balancers as enabled"() {
    when:
      def serverGroup = new KubernetesServerGroup(new ReplicationController(), ACCOUNT, [], null)
      serverGroup.instances = [] as Set
      serverGroup.replicas = 1
      serverGroup.labels = [(KubernetesUtil.loadBalancerKey("1")): "true"]

    then:
      !serverGroup.isDisabled()
  }

  void "Should list servergroup with mix of load balancers as enabled"() {
    when:
      def serverGroup = new KubernetesServerGroup(new ReplicationController(), ACCOUNT, [], null)
      serverGroup.instances = [] as Set
      serverGroup.replicas = 1
      serverGroup.labels = [(KubernetesUtil.loadBalancerKey("1")): "true", (KubernetesUtil.loadBalancerKey("2")): "false"]

    then:
      !serverGroup.isDisabled()
  }

  void "Should list servergroup with enabled load balancers but no instances as disabled"() {
    when:
    def serverGroup = new KubernetesServerGroup(new ReplicationController(), ACCOUNT, [], null)
    serverGroup.instances = [] as Set
    serverGroup.replicas = 0
    serverGroup.labels = [(KubernetesUtil.loadBalancerKey("1")): "true"]

    then:
    serverGroup.isDisabled()
  }
}
