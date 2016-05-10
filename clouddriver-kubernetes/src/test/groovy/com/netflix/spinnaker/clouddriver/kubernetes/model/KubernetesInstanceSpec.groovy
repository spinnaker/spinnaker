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
import io.fabric8.kubernetes.api.model.*
import spock.lang.Specification

class KubernetesInstanceSpec extends Specification {
  private final static String REPLICATION_CONTROLLER = "arcim"

  ContainerState containerStateAsRunningMock
  ContainerState containerStateAsTerminatedMock
  ContainerState containerStateAsWaitingMock
  ContainerState containerStateAsNoneMock

  ContainerStatus containerStatusAsRunningMock
  ContainerStatus containerStatusAsTerminatedMock
  ContainerStatus containerStatusAsWaitingMock
  ContainerStatus containerStatusAsNoneMock

  PodStatus podStatusMock
  ObjectMeta metadataMock
  Pod podMock

  def setup() {

    containerStateAsRunningMock = Mock(ContainerState)
    containerStateAsRunningMock.getRunning() >> new ContainerStateRunning()
    containerStateAsRunningMock.getTerminated() >> null
    containerStateAsRunningMock.getWaiting() >> null
    containerStatusAsRunningMock = Mock(ContainerStatus)
    containerStatusAsRunningMock.getReady() >> true
    containerStatusAsRunningMock.getState() >> containerStateAsRunningMock

    containerStateAsTerminatedMock = Mock(ContainerState)
    containerStateAsTerminatedMock.getRunning() >> null
    containerStateAsTerminatedMock.getTerminated() >> new ContainerStateTerminated()
    containerStateAsTerminatedMock.getWaiting() >> null
    containerStatusAsTerminatedMock = Mock(ContainerStatus)
    containerStatusAsTerminatedMock.getReady() >> false
    containerStatusAsTerminatedMock.getState() >> containerStateAsTerminatedMock

    containerStateAsWaitingMock = Mock(ContainerState)
    containerStateAsWaitingMock.getRunning() >> null
    containerStateAsWaitingMock.getTerminated() >> null
    containerStateAsWaitingMock.getWaiting() >> new ContainerStateWaiting()
    containerStatusAsWaitingMock = Mock(ContainerStatus)
    containerStatusAsWaitingMock.getReady() >> false
    containerStatusAsWaitingMock.getState() >> containerStateAsWaitingMock

    containerStateAsNoneMock = Mock(ContainerState)
    containerStateAsNoneMock.getRunning() >> null
    containerStateAsNoneMock.getTerminated() >> null
    containerStateAsNoneMock.getWaiting() >> null
    containerStatusAsNoneMock = Mock(ContainerStatus)
    containerStatusAsNoneMock.getReady() >> false
    containerStatusAsNoneMock.getState() >> containerStateAsNoneMock

    podStatusMock = Mock(PodStatus)
    metadataMock = Mock(ObjectMeta)
    podMock = Mock(Pod)

    podMock.getStatus() >> podStatusMock
    podMock.getMetadata() >> metadataMock

    // There is nothing interesting to test here, it is already handled by the
    // convertContainerState(..) tests.
    podStatusMock.getContainerStatuses() >> []
  }

  void "Should report state as Down"() {
    when:
      def state = (new KubernetesHealth('', containerStatusAsTerminatedMock)).state

    then:
      state == HealthState.Down
  }

  void "Should report state as Starting"() {
    when:
      def state = (new KubernetesHealth('', containerStatusAsWaitingMock)).state

    then:
      state == HealthState.Starting
  }

  void "Should report state as Up"() {
    when:
      def state = (new KubernetesHealth('', containerStatusAsRunningMock)).state

    then:
      state == HealthState.Up
  }

  void "Should report state as Unknown"() {
    when:
      def state = (new KubernetesHealth('', containerStatusAsNoneMock)).state

    then:
      state == HealthState.Unknown
  }

  void "Should report pod state as Up"() {
    setup:
      podStatusMock.getPhase() >> "Running"

    when:
      def instance = new KubernetesInstance(podMock, [])

    then:
      instance.healthState == HealthState.Up
  }

  void "Should report pod state as OOS"() {
    when:
      podStatusMock.getPhase() >> "Pending"
      def instance = new KubernetesInstance(podMock, [])

    then:
      instance.healthState == HealthState.OutOfService
  }

  void "Should report pod state as Unknown"() {
    setup:
      podStatusMock.getPhase() >> "floof"

    when:
      def instance = new KubernetesInstance(podMock, [])

    then:
      instance.healthState == HealthState.Unknown

    when:
      podStatusMock.getPhase() >> "Failed"
      instance = new KubernetesInstance(podMock, [])

    then:
      instance.healthState == HealthState.Unknown

  }

  void "Should report pod controller"() {
    setup:
      metadataMock.getLabels() >> ["foo": "bar", (KubernetesUtil.REPLICATION_CONTROLLER_LABEL): REPLICATION_CONTROLLER]

    when:
      def instance = new KubernetesInstance(podMock, [])

    then:
      instance.controllerName == REPLICATION_CONTROLLER
  }
}
