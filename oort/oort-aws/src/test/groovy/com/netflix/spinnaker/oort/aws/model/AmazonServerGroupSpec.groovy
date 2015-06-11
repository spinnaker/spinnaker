/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.oort.aws.model

import com.netflix.spinnaker.oort.model.HealthState
import com.netflix.spinnaker.oort.model.Instance
import com.netflix.spinnaker.oort.model.ServerGroup
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Created by zthrash on 1/7/15.
 */
class AmazonServerGroupSpec extends Specification {

  ServerGroup serverGroup

  def setup() {
    serverGroup = new AmazonServerGroup()
  }

  @Unroll
  void "getting instance status counts for all #state instances"() {
    given:
      serverGroup.instances = [buildAmazonInstance(state)]

    when:
      ServerGroup.InstanceCounts counts =  serverGroup.getInstanceCounts()

    then:
      counts.total == 1
      counts.up == (state == HealthState.Up ? 1 : 0)
      counts.down == (state == HealthState.Down ? 1 : 0)
      counts.unknown == (state == HealthState.Unknown ? 1 : 0)
      counts.starting == (state == HealthState.Starting ? 1 : 0)
      counts.outOfService == (state == HealthState.OutOfService ? 1 : 0)

    where:
      state                     | _
      HealthState.Up            | _
      HealthState.Down          | _
      HealthState.Unknown       | _
      HealthState.Starting      | _
      HealthState.OutOfService  | _
  }

  Instance buildAmazonInstance(HealthState state) {
    def instance = Mock(AmazonInstance)
    instance.getHealthState() >> state
    instance
  }

}
