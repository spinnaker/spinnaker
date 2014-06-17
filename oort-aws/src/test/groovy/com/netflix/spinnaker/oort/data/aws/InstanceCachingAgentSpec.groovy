/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.oort.data.aws

import com.amazonaws.services.ec2.model.*
import com.netflix.spinnaker.oort.data.aws.cachers.InstanceCachingAgent
import com.netflix.spinnaker.oort.security.aws.AmazonNamedAccount
import reactor.event.Event

class InstanceCachingAgentSpec extends AbstractCachingAgentSpec {

  InstanceCachingAgent getCachingAgent() {
    new InstanceCachingAgent(Mock(AmazonNamedAccount), "us-east-1")
  }

  void "new instances should fire newInstance event"() {
    setup:
    def reservation = Mock(Reservation)
    def instance = Mock(Instance)
    instance.getState() >> new InstanceState().withCode(16)
    reservation.getInstances() >> [instance]
    def result = new DescribeInstancesResult().withReservations(reservation)

    when:
    agent.load()

    then:
    1 * amazonEC2.describeInstances() >> result
    1 * reactor.notify("newInstance", _)
    0 * reactor.notify("missingInstance", _)
  }

  void "terminated instances should be removed"() {
    setup:
    def reservation = Mock(Reservation)
    def instance1 = new Instance().withInstanceId("i-12345").withState(new InstanceState().withCode(16))
    def instance2 = new Instance().withInstanceId("i-67890").withState(new InstanceState().withCode(16))
    reservation.getInstances() >> [instance1, instance2]
    def result = new DescribeInstancesResult().withReservations(reservation)

    when:
    "the brand new instances show up, they should be fired as newInstances"
    agent.load()

    then:
    1 * amazonEC2.describeInstances() >> result
    2 * reactor.notify("newInstance", _)

    when:
    "one of them is terminated, it should fire missingInstance"
    instance1.withState(new InstanceState().withCode(InstanceCachingAgent.TERMINATED))
    agent.load()

    then:
    1 * amazonEC2.describeInstances() >> result
    0 * reactor.notify("newInstance", _)
    1 * reactor.notify("missingInstance", _) >> { eventname, Event<InstanceCachingAgent.InstanceNotification> event ->
      assert event.data.instance.instanceId == "i-12345"
    }

    when:
    "the same results come back, it shouldnt do anything"
    agent.load()

    then:
    1 * amazonEC2.describeInstances() >> result
    0 * reactor.notify(_, _)
  }

  void "save and remove instances from cache"() {
    setup:
    def account = Mock(AmazonNamedAccount)
    def accountName = "test"
    account.getName() >> accountName
    def instanceId = "i-12345"
    def region = "us-east-1"
    def asgName = "oort-main-v000"
    def instance = new Instance().withInstanceId(instanceId).withTags(new Tag(InstanceCachingAgent.ASG_TAG_NAME, asgName))
    def event = Event.wrap(new InstanceCachingAgent.InstanceNotification(account, instance, region))
    def instanceCacheKey = Keys.getInstanceKey(instanceId, region)
    def instanceServerGroupCacheKey = Keys.getServerGroupInstanceKey(asgName, instanceId, accountName, region)

    when:
    "loading a new instance stores the instance in cache and associates it with the server group as a key reference"
    ((InstanceCachingAgent)agent).loadNewInstance(event)

    then:
    1 * cacheService.put(instanceCacheKey, instance)
    1 * cacheService.put(instanceServerGroupCacheKey, _)

    when:
    "removing an instance frees it and its server group reference from cache"
    ((InstanceCachingAgent)agent).removeMissingInstance(event)

    then:
    1 * cacheService.free(instanceCacheKey)
    1 * cacheService.free(instanceServerGroupCacheKey)
  }

}
