/*
 * Copyright 2016 Netflix, Inc.
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
 */


package com.netflix.spinnaker.clouddriver.aws.provider.agent

import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.SuspendedProcess
import spock.lang.Specification
import spock.lang.Unroll

class ClusterCachingAgentSpec extends Specification {
  static int defaultMin = 1
  static int defaultMax = 1
  static int defaultDesired = 1
  static Collection<String> defaultSuspendedProcesses = ["Launch"]

  AutoScalingGroup defaultAsg = new AutoScalingGroup()
    .withDesiredCapacity(defaultDesired)
    .withMinSize(defaultMin)
    .withMaxSize(defaultMax)
    .withSuspendedProcesses(defaultSuspendedProcesses.collect { new SuspendedProcess().withProcessName(it) }
  )

  @Unroll
  def "should compare capacity and suspended processes when determining if ASGs are similar"() {
    given:
    def asg = new AutoScalingGroup().withDesiredCapacity(desired).withMinSize(min).withMaxSize(max).withSuspendedProcesses(
      suspendedProcesses.collect { new SuspendedProcess().withProcessName(it) }
    )

    when:
    ClusterCachingAgent.areSimilarAutoScalingGroups(defaultAsg, asg) == areSimilar

    then:
    true

    where:
    min        | max        | desired        | suspendedProcesses        || areSimilar
    defaultMin | defaultMax | defaultDesired | defaultSuspendedProcesses || true
    0          | defaultMax | defaultDesired | defaultSuspendedProcesses || false
    defaultMin | 0          | defaultDesired | defaultSuspendedProcesses || false
    defaultMin | defaultMax | 0              | defaultSuspendedProcesses || false
    defaultMin | defaultMax | defaultDesired | []                        || false
  }

  private SuspendedProcess sP(String processName) {
    return new SuspendedProcess().withProcessName(processName)
  }
}
