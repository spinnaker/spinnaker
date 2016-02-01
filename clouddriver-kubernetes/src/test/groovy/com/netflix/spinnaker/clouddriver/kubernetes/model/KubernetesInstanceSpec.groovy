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
import io.fabric8.kubernetes.api.model.ContainerState
import io.fabric8.kubernetes.api.model.ContainerStateRunning
import io.fabric8.kubernetes.api.model.ContainerStateTerminated
import io.fabric8.kubernetes.api.model.ContainerStateWaiting
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.PodStatus
import spock.lang.Specification

class KubernetesInstanceSpec extends Specification {
  private final static String REPLICATION_CONTROLLER = "arcim"

  ContainerState containerStateAsRunningMock
  ContainerState containerStateAsTerminatedMock
  ContainerState containerStateAsWaitingMock
  ContainerState containerStateAsNoneMock

  PodStatus podStatusMock
  ObjectMeta metadataMock
  Pod podMock

  def setup() {

    containerStateAsRunningMock = Mock(ContainerState)
    containerStateAsRunningMock.getRunning() >> new ContainerStateRunning()
    containerStateAsRunningMock.getTerminated() >> null
    containerStateAsRunningMock.getWaiting() >> null

    containerStateAsTerminatedMock = Mock(ContainerState)
    containerStateAsTerminatedMock.getRunning() >> null
    containerStateAsTerminatedMock.getTerminated() >> new ContainerStateTerminated()
    containerStateAsTerminatedMock.getWaiting() >> null

    containerStateAsWaitingMock = Mock(ContainerState)
    containerStateAsWaitingMock.getRunning() >> null
    containerStateAsWaitingMock.getTerminated() >> null
    containerStateAsWaitingMock.getWaiting() >> new ContainerStateWaiting()

    containerStateAsNoneMock = Mock(ContainerState)
    containerStateAsNoneMock.getRunning() >> null
    containerStateAsNoneMock.getTerminated() >> null
    containerStateAsNoneMock.getWaiting() >> null

    podStatusMock = Mock(PodStatus)
    metadataMock = Mock(ObjectMeta)
    podMock = Mock(Pod)

    podMock.getStatus() >> podStatusMock
    podMock.getMetadata() >> metadataMock

    // There is nothing interesting to test here, it is already handled by the
    // convertContainerState(..) tests.
    podStatusMock.getContainerStatuses() >> []
  }

  void "Should report state as Up"() {
    when:
      def state = KubernetesInstance.convertContainerState(containerStateAsRunningMock)

    then:
      state == HealthState.Up
  }

  void "Should report state as Down"() {
    when:
      def state = KubernetesInstance.convertContainerState(containerStateAsTerminatedMock)

    then:
      state == HealthState.Down
  }

  void "Should report state as Starting"() {
    when:
      def state = KubernetesInstance.convertContainerState(containerStateAsWaitingMock)

    then:
      state == HealthState.Starting
  }

  void "Should report state as Unknown"() {
    when:
      def state = KubernetesInstance.convertContainerState(containerStateAsNoneMock)

    then:
      state == HealthState.Unknown

    when:
      state = KubernetesInstance.convertContainerState(null)

    then:
      state == HealthState.Unknown
  }

  void "Should report pod state as Up"() {
    setup:
      podStatusMock.getPhase() >> "Running"

    when:
      def instance = new KubernetesInstance(podMock)

    then:
      instance.healthState == HealthState.Up
  }

  void "Should report pod state as Down"() {
    setup:
      podStatusMock.getPhase() >> "Failed"

    when:
      def instance = new KubernetesInstance(podMock)

    then:
      instance.healthState == HealthState.Down
  }

  void "Should report pod state as Starting"() {
    setup:
      podStatusMock.getPhase() >> "Pending"

    when:
      def instance = new KubernetesInstance(podMock)

    then:
      instance.healthState == HealthState.Starting
  }

  void "Should report pod state as Unknown"() {
    setup:
      podStatusMock.getPhase() >> "floof"

    when:
      def instance = new KubernetesInstance(podMock)

    then:
      instance.healthState == HealthState.Unknown
  }

  void "Should report pod controller"() {
    setup:
      metadataMock.getLabels() >> ["foo": "bar", (KubernetesUtil.REPLICATION_CONTROLLER_LABEL): REPLICATION_CONTROLLER]

    when:
      def instance = new KubernetesInstance(podMock)

    then:
      instance.serverGroupName == REPLICATION_CONTROLLER
  }
}
