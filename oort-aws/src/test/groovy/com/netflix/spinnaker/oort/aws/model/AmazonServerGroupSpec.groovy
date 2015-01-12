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

/**
 * Created by zthrash on 1/7/15.
 */
class AmazonServerGroupSpec extends Specification {

  ServerGroup serverGroup

  def setup() {
    serverGroup = new AmazonServerGroup()
  }

  void "getting instance status counts for all up instances"() {
    given:
      serverGroup.instances = [buildUpAmazonInstance()]

    when:
      ServerGroup.InstanceCounts counts =  serverGroup.getInstanceCounts()
    then:
      counts.total == 1
      counts.up == 1
      counts.down == 0
      counts.unknown == 0
  }

  void "getting instance status counts for all down instances"() {
    given:
      serverGroup.instances = [buildDownAmazonInstance()]

    when:
      ServerGroup.InstanceCounts counts =  serverGroup.getInstanceCounts()
    then:
      counts.total == 1
      counts.up == 0
      counts.down == 1
      counts.unknown == 0
  }

  void "getting instance status counts for all unknown instances"() {
    given:
    serverGroup.instances = [buildUnknownAmazonInstance()]

    when:
    ServerGroup.InstanceCounts counts =  serverGroup.getInstanceCounts()
    then:
    counts.total == 1
    counts.up == 0
    counts.down == 0
    counts.unknown == 1
  }

  Instance buildUpAmazonInstance() {
    def instance = Mock(AmazonInstance)
    instance.getHealthState() >> HealthState.Up
    instance
  }

  Instance buildDownAmazonInstance() {
    def instance = Mock(AmazonInstance)
    instance.getHealthState() >> HealthState.Down
    instance
  }

  Instance buildUnknownAmazonInstance() {
    def instance = Mock(AmazonInstance)
    instance.getHealthState() >> HealthState.Unknown
    instance
  }
}
