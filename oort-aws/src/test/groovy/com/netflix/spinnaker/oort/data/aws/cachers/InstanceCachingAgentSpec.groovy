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

package com.netflix.spinnaker.oort.data.aws.cachers

import com.amazonaws.services.ec2.model.*
import com.netflix.spinnaker.amos.aws.NetflixAmazonCredentials
import com.netflix.spinnaker.oort.data.aws.Keys
import com.netflix.spinnaker.oort.data.aws.cachers.InstanceCachingAgent.InstanceStateValue

class InstanceCachingAgentSpec extends AbstractCachingAgentSpec {

  InstanceCachingAgent getCachingAgent() {
    new InstanceCachingAgent(Mock(NetflixAmazonCredentials), REGION)
  }

  void "new instances should fire newInstance event"() {
    setup:
    def instance = new Instance().withInstanceId('i-12345').withState(InstanceStateValue.Running.buildInstanceState())
    def reservation = new Reservation().withInstances(instance)
    def result = new DescribeInstancesResult().withReservations(reservation)

    when:
    agent.load()

    then:
    1 * amazonEC2.describeInstances(_) >> result
    1 * cacheService.put(Keys.getInstanceKey(instance.instanceId, REGION), instance)
  }

  void "terminated instances should be removed"() {
    setup:
    def instance1 = new Instance().withInstanceId("i-12345").withState(InstanceStateValue.Running.buildInstanceState())
    def instance2 = new Instance().withInstanceId("i-67890").withState(InstanceStateValue.Running.buildInstanceState())
    def reservation = new Reservation().withInstances(instance1, instance2)
    def result = new DescribeInstancesResult().withReservations(reservation)

    when:
    "the brand new instances show up, they should be fired as newInstances"
    agent.load()

    then:
    1 * amazonEC2.describeInstances(_) >> result
    1 * cacheService.put(Keys.getInstanceKey(instance1.instanceId, REGION), instance1)
    1 * cacheService.put(Keys.getInstanceKey(instance2.instanceId, REGION), instance2)

    when:
    "one of them is terminated, it should fire missingInstance"
    instance1.setState(InstanceStateValue.Terminated.buildInstanceState())
    agent.load()

    then:
    1 * amazonEC2.describeInstances(_) >> result
    0 * cacheService.put(_, _)
    1 * cacheService.free(Keys.getInstanceKey(instance1.instanceId, REGION))

    when:
    "the same results come back, it shouldnt do anything"
    agent.load()

    then:
    1 * amazonEC2.describeInstances(_) >> result
    0 * cacheService.put(_, _)
    0 * cacheService.free(_)
  }

  void "save and remove instances from cache"() {
    setup:
    def account = Mock(NetflixAmazonCredentials)
    account.getName() >> ACCOUNT
    def instanceId = "i-12345"
    def asgName = "oort-main-v000"
    def instance = new Instance()
      .withInstanceId(instanceId)
      .withTags(new Tag(InstanceCachingAgent.ASG_TAG_NAME, asgName))
      .withState(InstanceStateValue.Running.buildInstanceState())
    def instanceCacheKey = Keys.getInstanceKey(instanceId, REGION)
    def instanceServerGroupCacheKey = Keys.getServerGroupInstanceKey(asgName, instanceId, ACCOUNT, REGION)

    when:
    "loading a new instance stores the instance in cache and associates it with the server group as a key reference"
    ((InstanceCachingAgent)agent).loadNewInstance(account, instance, REGION)

    then:
    1 * cacheService.put(instanceCacheKey, instance)
    1 * cacheService.put(instanceServerGroupCacheKey, _)

    when:
    "removing an instance frees it and its server group reference from cache"
    ((InstanceCachingAgent)agent).removeMissingInstance(account, instance, REGION)

    then:
    1 * cacheService.free(instanceCacheKey)
    1 * cacheService.free(instanceServerGroupCacheKey)
  }

}
