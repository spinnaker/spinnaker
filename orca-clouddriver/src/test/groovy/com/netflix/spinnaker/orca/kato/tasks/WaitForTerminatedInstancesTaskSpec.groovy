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

package com.netflix.spinnaker.orca.kato.tasks

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.pipeline.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import retrofit.RetrofitError
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class WaitForTerminatedInstancesTaskSpec extends Specification {

  OortHelper oortHelper = Mock(OortHelper)
  @Subject
    task = new WaitForTerminatedInstancesTask(oortHelper: oortHelper)

  @Unroll
  void "should return #taskStatus status when #matches found via oort search"() {
    def stage = new PipelineStage(pipeline, "whatever", [
      "terminate.instance.ids": [instanceId]
    ]).asImmutable()

    when:
    def result = task.execute(stage)

    then:
    1 * oortHelper.getSearchResults(instanceId, 'instances', 'aws') >> [[totalMatches: matches]]
    result.status == taskStatus
    result.stageOutputs.'terminate.remaining.ids'?.size() == (matches ? matches : null)


    where:
    matches || taskStatus
    0       || ExecutionStatus.SUCCEEDED
    1       || ExecutionStatus.RUNNING

    pipeline = new Pipeline()
    instanceId = 'i-123456'
  }

  void "should return RUNNING status when search returns error"() {
    given:
    def stage = new PipelineStage(pipeline, "whatever", [
      "terminate.instance.ids": [instanceId]
    ]).asImmutable()

    when:
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.RUNNING
    result.stageOutputs.'terminate.remaining.ids' == [instanceId]

    1 * oortHelper.getSearchResults(instanceId, 'instances', 'aws') >> {
      throw RetrofitError.networkError("url", new IOException())
    }

    where:
    pipeline = new Pipeline()
    instanceId = 'i-123456'
  }

  void "should return RUNNING status when search returns multiple result sets"() {
    given:
    def stage = new PipelineStage(pipeline, "whatever", [
      "terminate.instance.ids": [instanceId]
    ]).asImmutable()

    when:
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.RUNNING
    1 * oortHelper.getSearchResults(instanceId, 'instances', 'aws') >> multipleResults

    where:
    pipeline = new Pipeline()
    instanceId = 'i-123456'
    multipleResults = [[:], [:]]
  }

  void "should search all instanceIds"() {
    given:
    def stage = new PipelineStage(pipeline, "whatever", [
      "terminate.instance.ids": instanceIds
    ]).asImmutable()

    when:
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED
    1 * oortHelper.getSearchResults(instanceIds[0], 'instances', 'aws') >> emptyResult
    1 * oortHelper.getSearchResults(instanceIds[1], 'instances', 'aws') >> emptyResult

    where:
    pipeline = new Pipeline()
    instanceIds = ['i-123456', 'i-654321']
    emptyResult = [["totalMatches": 0]]

  }

  void "should return running if any instance found via search"() {
    given:
    def stage = new PipelineStage(pipeline, "whatever", [
      "terminate.instance.ids": instanceIds
    ]).asImmutable()
    def expected = instanceIds.collect { it }

    when:
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.RUNNING
    //the collection is shuffled on each iteration of the task to avoid repeatedly checking the same instance for
    //termination, however the search also short circuits so the emptyResult instance may not be searched
    (0..1) * oortHelper.getSearchResults(instanceIds[0], 'instances', 'aws') >> {
      expected.remove(instanceIds[0])
      emptyResult
    }
    1 * oortHelper.getSearchResults(instanceIds[1], 'instances', 'aws') >> successfulMatch
    result.stageOutputs.'terminate.remaining.ids'.sort() == expected

    where:
    pipeline = new Pipeline()
    instanceIds = ['i-123456', 'i-654321']
    successfulMatch = [
      [totalMatches: 1]
    ]
    emptyResult = [
      [totalMatches: 0]
    ]
  }

  void "should filter expected instanceIds from serverGroup result"() {
    given:
    def stage = new PipelineStage(pipeline, "whatever", [
      "serverGroupName"       : serverGroupName,
      "terminate.account.name": account,
      "terminate.region"      : location.value,
      "terminate.instance.ids": instanceIds,
      "cloudProvider"         : cloudProvider
    ]).asImmutable()

    when:
    def result = task.execute(stage)

    then:
    1 * oortHelper.getTargetServerGroup(account, serverGroupName, location.value, cloudProvider) >> response
    result.status == expectedStatus
    result.stageOutputs."terminate.remaining.ids" == expectedIds

    where:

    instanceIds            | resultIds   | expectedIds | expectedStatus
    ['i-12345', 'i-23456'] | ['i-12345'] | ['i-12345'] | ExecutionStatus.RUNNING
    ['i-12345', 'i-23456'] | []          | null        | ExecutionStatus.SUCCEEDED
    ['i-12345', 'i-23456'] | ['i-34567'] | null        | ExecutionStatus.SUCCEEDED

    pipeline = new Pipeline()
    account = 'test'
    region = 'us-east-1'
    serverGroupName = 'foo-test-v001'
    cloudProvider = 'aws'
    location = TargetServerGroup.Support.locationFromCloudProviderValue(cloudProvider, region)
    serverGroup = [
      name                     : serverGroupName,
      type                     : cloudProvider,
      (location.singularType()): location.value,
      instances                : resultIds.collect { [name: it] }
    ]
    targetServerGroup = new TargetServerGroup(serverGroup: serverGroup)
    response = Optional.of(targetServerGroup)
  }
}
