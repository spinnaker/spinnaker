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

package com.netflix.spinnaker.orca.clouddriver.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.clouddriver.tasks.ShrinkClusterTask.CompositeComparitor
import com.netflix.spinnaker.orca.clouddriver.tasks.ShrinkClusterTask.CreatedTime
import com.netflix.spinnaker.orca.clouddriver.tasks.ShrinkClusterTask.InstanceCount
import com.netflix.spinnaker.orca.clouddriver.tasks.ShrinkClusterTask.IsActive
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.OrchestrationStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import retrofit.client.Response
import retrofit.mime.TypedByteArray
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import java.util.concurrent.atomic.AtomicInteger

class ShrinkClusterTaskSpec extends Specification {

  static AtomicInteger inc = new AtomicInteger(100)
  OortService oortService = Mock(OortService)
  KatoService katoService = Mock(KatoService)
  ObjectMapper objectMapper = new ObjectMapper()
  OortHelper oortHelper = new OortHelper(objectMapper: objectMapper, oortService: oortService)

  @Subject
  ShrinkClusterTask task

  def setup() {
    task = new ShrinkClusterTask(oortHelper: oortHelper, katoService: katoService)
  }

  @Unroll
  def "stage context #desc"() {
    setup:
    Stage orchestration = new OrchestrationStage(new Orchestration(), 'test', context)
    Map cluster = [serverGroups: serverGroups]
    Response oortResponse = new Response('http://clouddriver', 200, 'OK', [], new TypedByteArray('application/json', objectMapper.writeValueAsBytes(cluster)))

    when:
    def result = task.execute(orchestration)

    then:
    1 * oortService.getCluster('foo', 'test', 'foo-test', 'aws') >> oortResponse

    (expectedItems ? 1 : 0) * katoService.requestOperations('aws', _) >> { p, ops ->
      assert ops.size == expected.size()
      expected.each { expect ->
        assert ops.find { it.destroyServerGroup.serverGroupName == expect.name && it.destroyServerGroup.region == expect.region }
      }
      rx.Observable.just(new TaskId('1'))
    }

    where:
    allowDeleteActive | shrinkToSize | retainLargerOverNewer | serverGroups                                                             | expectedItems | desc
    null              | 1            | true                  | [mkSG(), mkSG()]                                                         | []            | 'default allowDeleteActive false'
    false             | null         | true                  | [mkSG(health: 'Down'), mkSG(health: 'Down'), mkSG()]                     | [0, 1]        | 'default shrinkToSize 1'
    false             | 1            | null                  | [mkSG(health: 'Down', instances: 5), mkSG(health: 'Down', instances: 4)] | [0]           | 'default retainLargerOverNewer false'
    false             | "2"          | false                 | [mkSG(health: 'Down'), mkSG(health: 'Down'), mkSG(health: 'Down')]       | [0]           | 'handles shrinkToSize as a String'
    false             | 2.0          | false                 | [mkSG(health: 'Down'), mkSG(health: 'Down'), mkSG(health: 'Down')]       | [0]           | 'handles shrinkToSize as a Double'
    "true"            | 1            | false                 | [mkSG(), mkSG()]                                                         | [0]           | 'handles allowDeleteActive as String'
    true              | 1            | "true"                | [mkSG(instances: 5), mkSG(instances: 4)]                                 | [1]           | 'handles largerOverNewer as String'


    context = mkCtx(allowDeleteActive, shrinkToSize, retainLargerOverNewer)
    expected = expectedItems.collect { serverGroups[it] }
  }

  @Unroll
  def "deletion priority #desc"() {

    when:
    def toDelete = task.getDeletionServerGroups(serverGroups, retainLargerOverNewer, allowDeleteActive, shrinkToSize)

    then:
    toDelete == expected

    where:
    //test data generation is a bit dirty - serverGroups is the data set, expectedItems is the indexes in that set in deletion
    //order
    //each invocation of mkSG creates a 'newer' ServerGroup than any previous invocation
    allowDeleteActive | shrinkToSize | retainLargerOverNewer | serverGroups                                                    | expectedItems | desc
    true              | 1            | true                  | [mkSG(), mkSG(instances: 11), mkSG(instances: 9)]               | [0, 2]        | 'prefers size over age when retainLargerOverNewer'
    true              | 1            | false                 | [mkSG(), mkSG(instances: 11), mkSG(instances: 9)]               | [1, 0]        | 'prefers age over size when not retainLargerOverNewer'
    false             | 1            | true                  | [mkSG(), mkSG(instances: 11), mkSG(instances: 9)]               | []            | 'dont delete any active if allowDeleteActive is false'
    false             | 1            | true                  | [mkSG(health: 'Down'), mkSG(instances: 11), mkSG(instances: 9)] | [0]           | 'if at least shrinkToSize active groups are retained, dont delete'
    true              | 1            | true                  | [mkSG(), mkSG(), mkSG()]                                        | [1, 0]        | 'falls through to createdTime if size is equal'
    true              | 2            | false                 | [mkSG(), mkSG(), mkSG()]                                        | [0]           | 'keeps shrinkToSize items'
    true              | 4            | true                  | [mkSG(), mkSG(), mkSG()]                                        | []            | 'deletes nothing if less serverGroups than shrinkToSize'

    expected = expectedItems.collect { serverGroups[it] }
  }

  def 'instanceCount prefers larger'() {
    def larger = mkSG(instances: 10)
    def smaller = mkSG(instances: 9)

    expect:
    [smaller, larger].sort(true, new InstanceCount()) == [larger, smaller]
  }

  def 'createdTime prefers newer'() {
    def older = mkSG()
    def newer = mkSG()

    expect:
    [older, newer].sort(true, new CreatedTime()) == [newer, older]
  }

  def 'isActive prefers active'() {
    def inactive = mkSG(health: 'OutOfService')
    def active = mkSG()

    expect:
    [inactive, active].sort(true, new IsActive()) == [active, inactive]
  }

  def 'empty instances is inactive'() {
    def inactive = mkSG(instances: 0)
    def active = mkSG()

    expect:
    [inactive, active].sort(true, new IsActive()) == [active, inactive]
  }

  def 'compositeComparator applies in order'() {
    def olderButBigger = mkSG(instances: 10)
    def newerButSmaller = mkSG(instances: 5)
    def items = [olderButBigger, newerButSmaller]

    expect:
    items.sort(false, new CompositeComparitor([new CreatedTime(), new InstanceCount()])) == [newerButSmaller, olderButBigger]
    items.sort(false, new CompositeComparitor([new InstanceCount(), new CreatedTime()])) == [olderButBigger, newerButSmaller]
  }


  private Map<String, Object> mkCtx(Object allowDeleteActive, Object shrinkToSize, Object retainLargerOverNewer) {
    [
      cluster              : 'foo-test',
      credentials          : 'test',
      regions              : ['us-east-1', 'us-west-1'],
      allowDeleteActive    : allowDeleteActive,
      shrinkToSize         : shrinkToSize,
      retainLargerOverNewer: retainLargerOverNewer

    ]
  }

  private Map<String, Object> mkSG(Map params = [:]) {
    int instances = params.instances == null ? 10 : params.instances
    int ctr = inc.incrementAndGet()
    [
      name       : "foo-test-v$ctr".toString(),
      region     : params.region ?: 'us-east-1',
      createdTime: ctr.longValue(),
      instances  : instances ? (1..instances).collect {
        [
          instanceId : "i-$it".toString(),
          healthState: params.health ?: 'Up'
        ]
      } : [],
    ]
  }

}
