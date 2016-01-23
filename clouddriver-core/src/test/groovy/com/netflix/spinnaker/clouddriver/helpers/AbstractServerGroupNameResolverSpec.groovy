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

package com.netflix.spinnaker.clouddriver.helpers

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import spock.lang.Specification
import spock.lang.Unroll

import static AbstractServerGroupNameResolver.TakenSlot

class AbstractServerGroupNameResolverSpec extends Specification {

  static class TestServerGroupNameResolver extends AbstractServerGroupNameResolver {
    private final List<TakenSlot> takenSlots

    TestServerGroupNameResolver(List<TakenSlot> takenSlots) {
      this.takenSlots = takenSlots
    }

    @Override
    String getPhase() {
      return 'TEST-DEPLOY'
    }

    @Override
    String getRegion() {
      return 'test-region'
    }

    @Override
    List<TakenSlot> getTakenSlots(String clusterName) {
      return takenSlots
    }
  }

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  @Unroll
  void "next server group name should be #expected when takenSlots is #takenSlots and application is 'app', stack is '#stack', and details is '#details'"() {
    when:
    def serverGroupNameResolver = new TestServerGroupNameResolver(takenSlots)

    then:
    serverGroupNameResolver.resolveNextServerGroupName('app', stack, details, false) == expected

    where:
    stack   | details     | takenSlots                               | expected
    null    | null        | [buildTakenSlot('app-v000')]             | 'app-v001'
    'new'   | null        | null                                     | 'app-new-v000'
    'new'   | null        | []                                       | 'app-new-v000'
    'test'  | null        | [buildTakenSlot('app-test-v009')]        | 'app-test-v010'
    'dev'   | 'detail'    | [buildTakenSlot('app-dev-detail-v015')]  | 'app-dev-detail-v016'
    'prod'  | 'c0usca'    | [buildTakenSlot('app-prod-c0usca-v000')] | 'app-prod-c0usca-v001'
  }


  void "resolveNextServerGroupName should handle roll-over properly"() {
    setup:
    def takenSlots = [
      buildTakenSlot('app-dev-v997', 997),
      buildTakenSlot('app-dev-v998', 998),
      buildTakenSlot('app-dev-v999', 999),
      buildTakenSlot('app-dev-v000', 000)
    ]
    def serverGroupNameResolver = new TestServerGroupNameResolver(takenSlots)

    when:
    def nextServerGroupName = serverGroupNameResolver.resolveNextServerGroupName('app', 'dev', null, false)

    then:
    nextServerGroupName == 'app-dev-v001'
  }

  void "resolveNextServerGroupName should throw exception when namespace is exhausted"() {
    setup:
    def takenSlots = (0..999).collect {
      def sequenceNumber = String.format('%03d', it)
      buildTakenSlot("app-dev-v$sequenceNumber", it)
    }
    def serverGroupNameResolver = new TestServerGroupNameResolver(takenSlots)

    when:
    serverGroupNameResolver.resolveNextServerGroupName('app', 'dev', null, false)

    then:
    IllegalArgumentException exc = thrown()
    exc.message == "All server group names for cluster app-dev in test-region are taken."
  }

  void "resolveNextServerGroupName should yield v001 when most recent server group does not define sequence number"() {
    setup:
    def takenSlots = [
      buildTakenSlot('app-dev-v997', 997),
      buildTakenSlot('app-dev', 998),
    ]
    def serverGroupNameResolver = new TestServerGroupNameResolver(takenSlots)

    when:
    def nextServerGroupName = serverGroupNameResolver.resolveNextServerGroupName('app', 'dev', null, false)

    then:
    nextServerGroupName == 'app-dev-v001'
  }

  private buildTakenSlot(String serverGroupName, long createdTime = 0) {
    return new TakenSlot(
      serverGroupName: serverGroupName,
      sequence: Names.parseName(serverGroupName).sequence,
      createdTime: new Date(createdTime)
    )
  }

  void "should fail for invalid characters in the asg name"() {
    when:
    AbstractServerGroupNameResolver.generateServerGroupName("foo", "bar", "east!", 0, false)

    then:
    IllegalArgumentException e = thrown()
    e.message == "(Use alphanumeric characters only)"
  }

  void "application, stack, and freeform details make up the asg name"() {
    expect:
    AbstractServerGroupNameResolver.generateServerGroupName("foo", "bar", "east", 0, false) == "foo-bar-east-v000"
  }

  void "push sequence should be ignored when specified so"() {
    expect:
    AbstractServerGroupNameResolver.generateServerGroupName("foo", "bar", "east", 0, true) == "foo-bar-east"
  }

  void "application, and stack make up the asg name"() {
    expect:
    AbstractServerGroupNameResolver.generateServerGroupName("foo", "bar", null, 1, false) == "foo-bar-v001"
  }

  void "application and version make up the asg name"() {
    expect:
    AbstractServerGroupNameResolver.generateServerGroupName("foo", null, null, 1, false) == "foo-v001"
  }

  void "application, and freeform details make up the asg name"() {
    expect:
    AbstractServerGroupNameResolver.generateServerGroupName("foo", null, "east", 1, false) == "foo--east-v001"
  }
}
