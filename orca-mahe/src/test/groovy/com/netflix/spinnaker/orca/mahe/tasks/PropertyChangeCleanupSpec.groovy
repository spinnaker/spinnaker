/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.mahe.tasks

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.mahe.MaheService
import com.netflix.spinnaker.orca.mahe.PropertyAction
import com.netflix.spinnaker.orca.mahe.cleanup.FastPropertyCleanupListener
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import java.nio.channels.Pipe

import static com.netflix.spinnaker.orca.mahe.pipeline.CreatePropertyStage.PIPELINE_CONFIG_TYPE

class PropertyChangeCleanupSpec extends Specification {

  def repository = Stub(ExecutionRepository)
  def mahe = Mock(MaheService)
  @Subject def listener = new FastPropertyCleanupListener(mahe)

  @Unroll()
  def "a deleted property is restored to its original stage if the pipeline is #executionStatus and has matching original property"() {
    given:
    def pipeline = Pipeline
      .builder()
      .withStage(PIPELINE_CONFIG_TYPE, PIPELINE_CONFIG_TYPE)
      .withGlobalContext(originalProperties: [originalProperty], propertyAction: PropertyAction.DELETE)
      .build()

    repository.retrievePipeline(pipeline.id) >> pipeline

    when:
    listener.afterExecution(null, pipeline, executionStatus, false)

    then:
    1 * mahe.upsertProperty(originalProperty)

    where:
    propertyId = "test_rfletcher|mahe|test|us-west-1||||asg=mahe-test-v010|cluster=mahe-test"
    propertyEnv = "test"
    originalProperty = [property: createPropertyWithId(propertyId)]
    executionStatus << [ExecutionStatus.TERMINAL, ExecutionStatus.CANCELED]
  }

  @Unroll()
  def "a update property does not revert if original state is missing and the pipeline is #executionStatus"() {
    given:
    def pipeline = Pipeline
      .builder()
      .withStage(PIPELINE_CONFIG_TYPE, PIPELINE_CONFIG_TYPE)
      .withGlobalContext(propertyIdList: [[propertyId: propertyId]], propertyAction: PropertyAction.UPDATE)
      .build()

    repository.retrievePipeline(pipeline.id) >> pipeline

    when:
    listener.afterExecution(null, pipeline, executionStatus, false)

    then:
    0 * mahe.upsertProperty(_)
    0 * mahe.deleteProperty(_, _, _)

    where:
    propertyId = "test_rfletcher|mahe|test|us-west-1||||asg=mahe-test-v010|cluster=mahe-test"
    propertyEnv = "test"
    executionStatus << [ExecutionStatus.TERMINAL, ExecutionStatus.CANCELED]
  }

  @Unroll()
  def "a newly created property should be deleted if the pipeline is #executionStatus and has matching original property"() {
    given:
    def pipeline = Pipeline
      .builder()
      .withStage(PIPELINE_CONFIG_TYPE, PIPELINE_CONFIG_TYPE)
      .withGlobalContext(propertyIdList: [[propertyId: propertyId]], originalProperties: [], propertyAction: PropertyAction.CREATE)
      .build()

    repository.retrievePipeline(pipeline.id) >> pipeline

    when:
    listener.afterExecution(null, pipeline, executionStatus, false)

    then:
    1 * mahe.deleteProperty(propertyId, 'spinnaker rollback',  originalProperty.env)

    where:
    propertyId = "test_rfletcher|mahe|test|us-west-1||||asg=mahe-test-v010|cluster=mahe-test"
    propertyEnv = "test"
    originalProperty = createPropertyWithId(propertyId)
    executionStatus << [ExecutionStatus.TERMINAL, ExecutionStatus.CANCELED]
  }


  def "a property created by a pipeline stage marked for 'rollback' is cleaned up at the end"() {
    given:
    def pipeline = Pipeline
      .builder()
      .withStage(PIPELINE_CONFIG_TYPE, PIPELINE_CONFIG_TYPE)
      .withGlobalContext(propertyIdList: [[propertyId: propertyId]], originalProperties: [], rollbackProperties: true, propertyAction: PropertyAction.CREATE)
      .build()
    repository.retrievePipeline(pipeline.id) >> pipeline

    when:
    listener.afterExecution(null, pipeline, null, true)

    then:
    1 * mahe.deleteProperty(propertyId, "spinnaker rollback", propertyEnv)

    where:
    propertyId = "test_rfletcher|mahe|test|us-west-1||||asg=mahe-test-v010|cluster=mahe-test"
    propertyEnv = "test"
  }

  def "a property updated by a pipeline stage is cleaned up at the end"() {
    given:
    def pipeline = Pipeline
      .builder()
      .withStage(PIPELINE_CONFIG_TYPE, PIPELINE_CONFIG_TYPE)
      .withGlobalContext(propertyIdList: [[propertyId: propertyId]], originalProperties: [previous], rollbackProperties: true, propertyAction: PropertyAction.UPDATE)
      .build()
    repository.retrievePipeline(pipeline.id) >> pipeline

    when:
    listener.afterExecution(null, pipeline, null, true)

    then:
    1 * mahe.upsertProperty(previous)

    where:
    propertyId = "test_rfletcher|mahe|test|us-west-1||||asg=mahe-test-v010|cluster=mahe-test"
    propertyEnv = "test"
    previous =  [property: createPropertyWithId(propertyId)]
  }

  def "a property not marked for 'rollback' and is deleted by pipeline stage and is not reverted"() {
    given:
    def pipeline = Pipeline
      .builder()
      .withStage(PIPELINE_CONFIG_TYPE, PIPELINE_CONFIG_TYPE)
      .withGlobalContext(propertyIdList: [propertyId], originalProperties: [previous], rollbackProperties: false, propertyAction: PropertyAction.DELETE )
      .build()
    repository.retrievePipeline(pipeline.id) >> pipeline

    when:
    listener.afterExecution(null, pipeline, null, true)

    then:
    0 * mahe.upsertProperty([property: previous])
    0 * mahe.deleteProperty(_, _, _)

    where:
    propertyId = "test_rfletcher|mahe|test|us-west-1||||asg=mahe-test-v010|cluster=mahe-test"
    propertyEnv = "test"
    previous =  createPropertyWithId(propertyId)

  }



  def createPropertyWithId(propertyId) {
    [
      "propertyId"     : propertyId,
      "key"            : "test_rfletcher",
      "value"          : "test4",
      "env"            : "test",
      "appId"          : "mahe",
      "region"         : "us-west-1",
      "cluster"        : "mahe-test",
      "asg"            : "mahe-test-v010",
      "updatedBy"      : "rfletcher",
      "sourceOfUpdate" : "postman",
      "cmcTicket"      : "rfletcher",
      "ttl"            : 0,
      "ts"             : "2016-03-16T18:20:29.554Z[GMT]",
      "createdAsCanary": false ]
  }

}
