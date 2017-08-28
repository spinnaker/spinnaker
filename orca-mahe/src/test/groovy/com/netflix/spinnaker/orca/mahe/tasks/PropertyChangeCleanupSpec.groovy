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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.mahe.MaheService
import com.netflix.spinnaker.orca.mahe.PropertyAction
import com.netflix.spinnaker.orca.mahe.cleanup.FastPropertyCleanupListener
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import retrofit.client.Response
import retrofit.mime.TypedByteArray
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.mahe.PropertyAction.DELETE
import static com.netflix.spinnaker.orca.mahe.pipeline.CreatePropertyStage.PIPELINE_CONFIG_TYPE
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class PropertyChangeCleanupSpec extends Specification {

  ObjectMapper mapper = new ObjectMapper()
  def repository = Stub(ExecutionRepository)
  def mahe = Mock(MaheService)
  @Subject def listener = new FastPropertyCleanupListener(mahe)

  def setup() {
    listener.mapper = mapper
  }

  @Unroll()
  def "a deleted property is restored to its original stage if the pipeline is #executionStatus and has matching original property"() {
    given:
    def propertyContext = [propertyAction: DELETE.toString(), originalProperties: [[property: originalProperty]]]
    def pipeline = pipeline {
      stage {
        type = PIPELINE_CONFIG_TYPE
        name = PIPELINE_CONFIG_TYPE
        context = propertyContext
      }
      context = propertyContext
    }

    repository.retrievePipeline(pipeline.id) >> pipeline

    when:
    listener.afterExecution(null, pipeline, executionStatus, false)

    then:
    1 * mahe.upsertProperty(_) >> { Map res ->
      String propId = "${res.property.key}|${res.property.value}"
      def json = mapper.writeValueAsString([propertyId: propId])
      new Response("http://mahe", 200, "OK", [], new TypedByteArray('application/json', json.bytes))
    }

    where:
    propertyId = "test_rfletcher|mahe|test|us-west-1||||asg=mahe-test-v010|cluster=mahe-test"
    propertyEnv = "test"
    originalProperty = createPropertyWithId(propertyId)
    executionStatus << [ExecutionStatus.TERMINAL, ExecutionStatus.CANCELED]
  }

  def "failed upsert rollback should throw an IllegalStateException"() {
    given:
    def propertyContext = [originalProperties: [[property: originalProperty]], propertyAction: DELETE.toString()]
    def pipeline = pipeline {
      stage {
        type = PIPELINE_CONFIG_TYPE
        name = PIPELINE_CONFIG_TYPE
        context = propertyContext
      }
      context = propertyContext
    }

    repository.retrievePipeline(pipeline.id) >> pipeline

    when:
    listener.afterExecution(null, pipeline, executionStatus, false)

    then:
    1 * mahe.upsertProperty(_) >> { Map res ->
      new Response("http://mahe", 500, "OK", [], null)
    }

    pipeline.context.rollbackActions == null

    IllegalStateException ex = thrown()
    assert ex.message.contains("Unable to rollback DELETE")

    where:
    propertyId = "test_rfletcher|mahe|test|us-west-1||||asg=mahe-test-v010|cluster=mahe-test"
    propertyEnv = "test"
    originalProperty = createPropertyWithId(propertyId)
    executionStatus = ExecutionStatus.TERMINAL
  }

  @Unroll()
  def "a update property does not revert if original state is missing and the pipeline is #executionStatus"() {
    given:
    def pipeline = pipeline {
      stage {
        type = PIPELINE_CONFIG_TYPE
        name = PIPELINE_CONFIG_TYPE
      }
      context = [propertyIdList: [[propertyId: propertyId]], propertyAction: PropertyAction.UPDATE.toString()]
    }

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
  def "a newly created property should be deleted if the pipeline status is #executionStatus and has matching original property"() {
    given:
    def propertyContext = [propertyIdList: [[propertyId: propertyId]], originalProperties: [], propertyAction: PropertyAction.CREATE]
    def pipeline = pipeline {
      stage {
        type = PIPELINE_CONFIG_TYPE
        name = PIPELINE_CONFIG_TYPE
        context = propertyContext
      }
      context = propertyContext
    }

    repository.retrievePipeline(pipeline.id) >> pipeline

    when:
    listener.afterExecution(null, pipeline, executionStatus, false)

    then:
    1 * mahe.deleteProperty(propertyId, 'spinnaker rollback', propertyEnv) >> { def res ->
      new Response("http://mahe", 200, "OK", [], null)
    }

    where:
    propertyId = "test_rfletcher|mahe|test|us-west-1||||asg=mahe-test-v010|cluster=mahe-test"
    propertyEnv = "test"
    originalProperty = createPropertyWithId(propertyId)
    executionStatus << [ExecutionStatus.TERMINAL, ExecutionStatus.CANCELED]
  }

  def "failed rollback of delete should throw IllegalStateException"() {
    given:
    def propertyContext = [propertyIdList: [[propertyId: propertyId]], originalProperties: [], propertyAction: PropertyAction.CREATE]
    def pipeline = pipeline {
      stage {
        type = PIPELINE_CONFIG_TYPE
        name = PIPELINE_CONFIG_TYPE
        context = propertyContext
      }
      context = propertyContext
    }

    repository.retrievePipeline(pipeline.id) >> pipeline

    when:
    listener.afterExecution(null, pipeline, executionStatus, false)

    then:
    1 * mahe.deleteProperty(propertyId, 'spinnaker rollback', propertyEnv) >> { def res ->
      new Response("http://mahe", 500, "OK", [] , null)
    }

    pipeline.context.rollbackActions == null

    IllegalStateException ex = thrown()
    assert ex.message.contains("Unable to rollback CREATE")
    assert ex.message.contains(propertyId)

    where:
    propertyId = "test_rfletcher|mahe|test|us-west-1||||asg=mahe-test-v010|cluster=mahe-test"
    propertyEnv = "test"
    originalProperty = createPropertyWithId(propertyId)
    executionStatus << [ExecutionStatus.TERMINAL, ExecutionStatus.CANCELED]
  }

  def "a property created by a pipeline stage marked for 'rollback' is cleaned up at the end"() {
    given:
    def propertyContext = [propertyIdList: [[propertyId: propertyId]], originalProperties: [], rollback: true, propertyAction: PropertyAction.CREATE.toString()]
    def pipeline = pipeline {
      stage {
        type = PIPELINE_CONFIG_TYPE
        name = PIPELINE_CONFIG_TYPE
        context = propertyContext
      }
      context = propertyContext
    }
    repository.retrievePipeline(pipeline.id) >> pipeline

    when:
    listener.afterExecution(null, pipeline, null, true)

    then:
    1 * mahe.deleteProperty(propertyId, 'spinnaker rollback', propertyEnv) >> { def res ->
      def json = mapper.writeValueAsString([propertyId: propertyId])
      new Response("http://mahe", 200, "OK", [], new TypedByteArray('application/json', json.bytes))
    }

    where:
    propertyId = "test_rfletcher|mahe|test|us-west-1||||asg=mahe-test-v010|cluster=mahe-test"
    propertyEnv = "test"
  }

  def "a property updated by a pipeline stage is cleaned up at the end when marked for rollback"() {
    given:
    def propertyContext = [propertyIdList: [[propertyId: propertyId]], originalProperties: [previous], rollback: true, propertyAction: PropertyAction.UPDATE.toString()]
    def pipeline = pipeline {
      stage {
        type = PIPELINE_CONFIG_TYPE
        name = PIPELINE_CONFIG_TYPE
        context = propertyContext
      }
      context = propertyContext
    }
    repository.retrievePipeline(pipeline.id) >> pipeline

    when:
    listener.afterExecution(null, pipeline, null, true)

    then:
    1 * mahe.upsertProperty(previous) >> { Map res ->
      String propId = "${res.property.key}|${res.property.value}"
      def json = mapper.writeValueAsString([propertyId: propId])
      new Response("http://mahe", 200, "OK", [], new TypedByteArray('application/json', json.bytes))
    }

    where:
    propertyId = "test_rfletcher|mahe|test|us-west-1||||asg=mahe-test-v010|cluster=mahe-test"
    propertyEnv = "test"
    previous =  [property: createPropertyWithId(propertyId)]
  }

  def "a property not marked for 'rollback' and is deleted by pipeline stage and is not reverted"() {
    given:
    def pipeline = pipeline {
      stage {
        type = PIPELINE_CONFIG_TYPE
        name = PIPELINE_CONFIG_TYPE
      }
      context = [propertyIdList: [propertyId], originalProperties: [previous], rollbackProperties: false, propertyAction: DELETE]
    }
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

  def "rollback a pipeline with multiple create stages"() {
    given:
    def createStageContext = [propertyIdList: [[propertyId: propertyId]], originalProperties: [], propertyAction: PropertyAction.CREATE.toString()]
    def pipeline = pipeline {
      3.times {
        stage {
          type = PIPELINE_CONFIG_TYPE
          name = PIPELINE_CONFIG_TYPE
          context = createStageContext
        }
      }
    }

    when:
    listener.afterExecution(null, pipeline, ExecutionStatus.TERMINAL, true)

    then:

    3 * mahe.deleteProperty(propertyId, 'spinnaker rollback', propertyEnv) >> { def res ->
      def json = mapper.writeValueAsString([propertyId: propertyId])
      new Response("http://mahe", 200, "OK", [], new TypedByteArray('application/json', json.bytes))
    }

    where:
    propertyId = "test_rfletcher|mahe|test|us-west-1||||asg=mahe-test-v010|cluster=mahe-test"
    propertyEnv = "test"
    previous =  createPropertyWithId(propertyId)

  }

  def "rollback a pipeline with a create and a delete stages that are created for a scope update"() {
    given:
    def createStageContext = [propertyIdList: [[propertyId: propertyId]], originalProperties: [], propertyAction: PropertyAction.CREATE.toString()]
    def deleteStageContext = [propertyIdList: [[propertyId: propertyId]], originalProperties: [[property: previous]], propertyAction: DELETE.toString()]
    def pipeline = pipeline {
      stage {
        type = PIPELINE_CONFIG_TYPE
        name = PIPELINE_CONFIG_TYPE
        context = createStageContext
      }
      stage {
        type = PIPELINE_CONFIG_TYPE
        name = PIPELINE_CONFIG_TYPE
        context = deleteStageContext
      }
    }

    when:
    listener.afterExecution(null, pipeline, ExecutionStatus.TERMINAL, true)

    then:

    1 * mahe.deleteProperty(propertyId, 'spinnaker rollback', propertyEnv) >> { def res ->
      def json = mapper.writeValueAsString([propertyId: propertyId])
      new Response("http://mahe", 200, "OK", [], new TypedByteArray('application/json', json.bytes))
    }

    1 * mahe.upsertProperty(_) >> { Map res ->
      String propId = "${res.property.key}|${res.property.value}"
      def json = mapper.writeValueAsString([propertyId: propId])
      new Response("http://mahe", 200, "OK", [], new TypedByteArray('application/json', json.bytes))
    }

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
