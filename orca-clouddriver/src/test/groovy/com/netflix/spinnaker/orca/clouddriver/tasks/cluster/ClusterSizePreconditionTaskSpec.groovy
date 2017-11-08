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

package com.netflix.spinnaker.orca.clouddriver.tasks.cluster

import java.util.concurrent.atomic.AtomicInteger
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import retrofit.client.Response
import retrofit.mime.TypedByteArray
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class ClusterSizePreconditionTaskSpec extends Specification {

  static AtomicInteger cnt = new AtomicInteger(100)
  ObjectMapper objectMapper = new ObjectMapper()
  OortService oortService = Mock(OortService)

  @Subject
  ClusterSizePreconditionTask task = new ClusterSizePreconditionTask(objectMapper: objectMapper, oortService: oortService)

  @Unroll
  def 'ops test #actual #op #expected #result'() {
    expect:
    ClusterSizePreconditionTask.Operator.fromString(op).evaluate(actual, expected) == result

    where:
    actual | op   | expected | result
    1      | '<'  | 2        | true
    2      | '<'  | 1        | false
    2      | '<=' | 2        | true
    2      | '<=' | 1        | false
    1      | '==' | 1        | true
    1      | '==' | 2        | false
    2      | '>'  | 1        | true
    1      | '>'  | 2        | false
    2      | '>=' | 2        | true
    1      | '>=' | 2        | false
  }

  def 'checks cluster size'() {
    setup:
    def body = new TypedByteArray('application/json', objectMapper.writeValueAsBytes([
      serverGroups: serverGroups
    ]))
    def response = new Response('http://foo', 200, 'OK', [], body)
    def stage = new Stage(Execution.newPipeline("orca"), 'checkCluster', [
      context: [
        credentials: credentials,
        cluster    : cluster,
        regions    : regions
      ]
    ])

    when:
    def result = task.execute(stage)

    then:
    1 * oortService.getCluster('foo', 'test', 'foo', 'aws') >> response

    result.status == ExecutionStatus.SUCCEEDED

    where:
    credentials = 'test'
    cluster = 'foo'
    serverGroups = [mkSg('us-west-1'), mkSg('us-east-1')]

    regions | success
    ['us-west-1'] | true
    ['us-east-1'] | true
    ['us-west-1', 'us-east-1'] | true
  }

  def 'fails if cluster size wrong'() {
    setup:
    def body = new TypedByteArray('application/json', objectMapper.writeValueAsBytes([
      serverGroups: serverGroups
    ]))
    def response = new Response('http://foo', 200, 'OK', [], body)
    def stage = new Stage(Execution.newPipeline("orca"), 'checkCluster', [
      context: [
        credentials: credentials,
        cluster    : cluster,
        regions    : regions
      ]
    ])

    when:
    task.execute(stage)

    then:
    1 * oortService.getCluster('foo', 'test', 'foo', 'aws') >> response

    thrown(IllegalStateException)

    where:
    credentials = 'test'
    cluster = 'foo'
    serverGroups = [mkSg('us-west-1'), mkSg('us-east-1'), mkSg('us-west-2'), mkSg('us-west-2')]

    regions << [['eu-west-1'], ['eu-west-1', 'us-west-1'], ['us-east-1', 'us-west-2']]
  }


  @Unroll
  'cluster with name "#cluster" and moniker "#moniker" should have application name "#expected"'() {
    given:
    def stage = new Stage(Execution.newPipeline("orca"), 'checkCluster', [
      context: [
        cluster: cluster,
        moniker: moniker,
      ]
    ])
    when:
    ClusterSizePreconditionTask.ComparisonConfig config = stage.mapTo("/context", ClusterSizePreconditionTask.ComparisonConfig)

    then:
    config.getApplication() == expected

    where:
    cluster       | moniker            | expected
    'clustername' | ['app': 'appname'] | 'appname'
    'app-stack'   | null               | 'app'

  }


  Map mkSg(String region) {
    [name: "foo-v${cnt.incrementAndGet()}".toString(), region: region]
  }
}
