/*
 * Copyright 2016 Schibsted ASA.
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

package com.netflix.spinnaker.rosco.controllers

import com.google.common.collect.Sets
import com.netflix.spinnaker.rosco.api.BakeStatus
import com.netflix.spinnaker.rosco.controllers.StatusController
import com.netflix.spinnaker.rosco.persistence.RedisBackedBakeStore
import spock.lang.Specification
import spock.lang.Subject

class InstanceStatusEndpointSpec extends Specification {

  private static final String JOB_ID = "123"
  private
  static runningBakeStatus = new BakeStatus(id: JOB_ID, resource_id: JOB_ID, state: BakeStatus.State.RUNNING, result: null)
  private static String instanceId = UUID.randomUUID().toString()

  void 'instance incomplete bakes with status'() {
    setup:
    def bakeStoreMock = Mock(RedisBackedBakeStore)

    @Subject
    def statusHandler = new StatusController(bakeStoreMock, instanceId)

    when:
    def instanceInfo = statusHandler.instanceIncompleteBakes()

    then:
    1 * bakeStoreMock.getThisInstanceIncompleteBakeIds() >> Sets.newHashSet(JOB_ID)
    1 * bakeStoreMock.retrieveBakeStatusById(JOB_ID) >> runningBakeStatus
    instanceInfo.bakes == [runningBakeStatus]
    instanceInfo.status == "RUNNING"
  }

  void 'instance incomplete bakes without status'() {
    setup:
    def bakeStoreMock = Mock(RedisBackedBakeStore)
    bakeStoreMock.getThisInstanceIncompleteBakeIds() >> new HashSet<>()

    @Subject
    def statusHandler = new StatusController(bakeStoreMock, instanceId)

    when:
    def instanceInfo = statusHandler.instanceIncompleteBakes()

    then:
    1 * bakeStoreMock.getThisInstanceIncompleteBakeIds()
    instanceInfo.bakes.isEmpty()
    instanceInfo.status == "IDLE"
  }

  void 'no instance incomplete bakes'() {
    setup:
    def bakeStoreMock = Mock(RedisBackedBakeStore)

    @Subject
    def statusHandler = new StatusController(bakeStoreMock, instanceId)

    when:
    def instanceInfo = statusHandler.instanceIncompleteBakes()

    then:
    instanceInfo.bakes.isEmpty()
    instanceInfo.status == "IDLE"
  }

  void 'redis exception on get incomplete bakes'() {
    setup:
    def bakeStoreMock = Mock(RedisBackedBakeStore)
    bakeStoreMock.getThisInstanceIncompleteBakeIds() >> { throw new RuntimeException() }

    @Subject
    def statusHandler = new StatusController(bakeStoreMock, instanceId)

    when:
    statusHandler.instanceIncompleteBakes()

    then:
    thrown(RuntimeException)
  }

  void 'redis exception on get bakes result'() {
    setup:
    def bakeStoreMock = Mock(RedisBackedBakeStore)
    bakeStoreMock.getThisInstanceIncompleteBakeIds() >> Sets.newHashSet(JOB_ID)
    bakeStoreMock.retrieveBakeStatusById(JOB_ID) >> { throw new RuntimeException() }

    @Subject
    def statusHandler = new StatusController(bakeStoreMock, instanceId)

    when:
    statusHandler.instanceIncompleteBakes()

    then:
    thrown(RuntimeException)
  }

}
