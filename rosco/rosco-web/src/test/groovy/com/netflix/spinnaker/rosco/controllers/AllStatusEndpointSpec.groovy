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

class AllStatusEndpointSpec extends Specification {

  private static final String LOCAL_JOB_ID = "123"
  private static final String REMOTE_JOB_ID = "123"
  private static localRunningBakeStatus = new BakeStatus(id: LOCAL_JOB_ID, resource_id: LOCAL_JOB_ID, state: BakeStatus.State.RUNNING, result: null)
  private static remoteRunningBakeStatus = new BakeStatus(id: REMOTE_JOB_ID, resource_id: REMOTE_JOB_ID, state: BakeStatus.State.RUNNING, result: null)
  private static String localInstanceId = UUID.randomUUID().toString()
  private static String remoteInstanceId = UUID.randomUUID().toString()

  void 'all instances incomplete bakes with status'() {
    setup:
    def bakeStoreMock = Mock(RedisBackedBakeStore)

    @Subject
    def statusHandler = new StatusController(bakeStoreMock, localInstanceId)

    when:
    def instances = statusHandler.allIncompleteBakes()
    then:
    1 * bakeStoreMock.getAllIncompleteBakeIds() >> [(localInstanceId): Sets.newHashSet(LOCAL_JOB_ID), (remoteInstanceId): Sets.newHashSet(REMOTE_JOB_ID)]
    1 * bakeStoreMock.retrieveBakeStatusById(LOCAL_JOB_ID) >> localRunningBakeStatus
    1 * bakeStoreMock.retrieveBakeStatusById(REMOTE_JOB_ID) >> remoteRunningBakeStatus
    instances == [instance: localInstanceId, instances: [(localInstanceId): [status: "RUNNING", bakes: [localRunningBakeStatus]], (remoteInstanceId): [status: "RUNNING", bakes: [remoteRunningBakeStatus]]]]
  }

  void 'all instances incomplete bakes without status'() {
    setup:
    def bakeStoreMock = Mock(RedisBackedBakeStore)
    bakeStoreMock.getAllIncompleteBakeIds() >> new HashMap<String, Set<String>>()
    def statusHandler = new StatusController(bakeStoreMock, localInstanceId)

    when:
    def instances = statusHandler.allIncompleteBakes()

    then:
    1 * bakeStoreMock.getAllIncompleteBakeIds()
    instances == [instance: localInstanceId, instances: [:]]
  }

  void 'no instances incomplete bakes'() {
    setup:
    def bakeStoreMock = Mock(RedisBackedBakeStore)
    def statusHandler = new StatusController(bakeStoreMock, localInstanceId)

    when:
    def instances = statusHandler.allIncompleteBakes()

    then:
    instances == [instance: localInstanceId, instances: [:]]
  }

  void 'redis exception on get incomplete bakes'() {
    setup:
    def bakeStoreMock = Mock(RedisBackedBakeStore)
    bakeStoreMock.getAllIncompleteBakeIds() >> { throw new RuntimeException() }
    def statusHandler = new StatusController(bakeStoreMock, localInstanceId)

    when:
    statusHandler.allIncompleteBakes()

    then:
    thrown(RuntimeException)
  }

  void 'redis exception on get bakes result'() {
    setup:
    def bakeStoreMock = Mock(RedisBackedBakeStore)
    bakeStoreMock.getAllIncompleteBakeIds() >> [(localInstanceId): Sets.newHashSet(LOCAL_JOB_ID), (remoteInstanceId): Sets.newHashSet(REMOTE_JOB_ID)]
    bakeStoreMock.retrieveBakeStatusById(LOCAL_JOB_ID) >> { throw new RuntimeException() }
    bakeStoreMock.retrieveBakeStatusById(REMOTE_JOB_ID) >> { throw new RuntimeException() }
    def statusHandler = new StatusController(bakeStoreMock, localInstanceId)

    when:
    statusHandler.allIncompleteBakes()

    then:
    thrown(RuntimeException)
  }

}
