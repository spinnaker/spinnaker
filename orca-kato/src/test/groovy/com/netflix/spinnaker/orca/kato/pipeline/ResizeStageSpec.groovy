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

package com.netflix.spinnaker.orca.kato.pipeline

import com.netflix.spinnaker.orca.batch.TaskTaskletAdapter
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.oort.OortService
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.persistence.DefaultExecutionRepository
import com.netflix.spinnaker.orca.pipeline.persistence.memory.InMemoryOrchestrationStore
import com.netflix.spinnaker.orca.pipeline.persistence.memory.InMemoryPipelineStore
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.repository.JobRepository
import org.springframework.context.ApplicationContext
import org.springframework.transaction.PlatformTransactionManager
import retrofit.client.Response
import retrofit.mime.TypedByteArray
import spock.lang.Specification

class ResizeStageSpec extends Specification {

  def mapper = OrcaObjectMapper.DEFAULT
  def oort = Mock(OortService)
  def stageBuilder = new ResizeAsgStage(oort: oort, mapper: mapper)
  def pipelineStore = new InMemoryPipelineStore(mapper)
  def orchestrationStore = new InMemoryOrchestrationStore(mapper)
  def executionRepository = new DefaultExecutionRepository(orchestrationStore, pipelineStore)

  def setup() {
    stageBuilder.steps = new StepBuilderFactory(Stub(JobRepository), Stub(PlatformTransactionManager))
    stageBuilder.taskTaskletAdapter = new TaskTaskletAdapter(executionRepository, [])
    stageBuilder.applicationContext = Stub(ApplicationContext) {
      getBean(_) >> { Class type -> type.newInstance() }
    }
  }

  void "should create basic stage according to inputs"() {
    setup:
    def config = [asgName    : "testapp-asg-v000", regions: ["us-west-1", "us-east-1"], capacity: [min: 0, max: 0, desired: 0],
                  credentials: "test"]
    def pipeline = new Pipeline()
    def stage = new PipelineStage(pipeline, "resizeAsg", config)

    when:
    stageBuilder.buildSteps(stage)

    then:
    1 * oort.getCluster("testapp", "test", "testapp-asg", "aws") >> {
      def cluster = [serverGroups: [[
                                      name  : "testapp-asg-v000",
                                      region: "us-west-1",
                                      asg   : [
                                        minSize        : 5,
                                        maxSize        : 5,
                                        desiredCapacity: 5
                                      ]
                                    ], [
                                      name  : "testapp-asg-v000",
                                      region: "us-east-1",
                                      asg   : [
                                        minSize        : 5,
                                        maxSize        : 5,
                                        desiredCapacity: 5
                                      ]
                                    ]]]
      new Response(
        "foo", 200, "ok", [],
        new TypedByteArray(
          "application/json",
          mapper.writeValueAsBytes(cluster)
        )
      )
    }
    0 == stageBuilder.beforeStages.size()
    0 == stageBuilder.afterStages.size()
    stage.context == config
  }

  void "should create basic stage derived from cluster name and target"() {
    setup:
    def config = [cluster : "testapp-asg", target: target, regions: ["us-west-1", "us-east-1"],
                  capacity: [min: 0, max: 0, desired: 0], credentials: "test"]
    def pipeline = new Pipeline()
    def stage = new PipelineStage(pipeline, "resizeAsg", config)

    when:
    stageBuilder.buildSteps(stage)

    then:
    1 * oort.getCluster("testapp", "test", "testapp-asg", "aws") >> {
      def cluster = [serverGroups: [[
                                      name  : "testapp-asg-v000",
                                      region: "us-west-1",
                                      asg   : [
                                        minSize        : 5,
                                        maxSize        : 5,
                                        desiredCapacity: 5
                                      ]
                                    ], [
                                      name  : "testapp-asg-v001",
                                      region: "us-west-1",
                                      asg   : [
                                        minSize        : 5,
                                        maxSize        : 5,
                                        desiredCapacity: 5
                                      ]
                                    ]]]
      new Response(
        "foo", 200, "ok", [],
        new TypedByteArray(
          "application/json",
          mapper.writeValueAsBytes(cluster)
        )
      )
    }
    stage.context.asgName == asgName

    where:
    target         | asgName
    "current_asg"  | "testapp-asg-v001"
    "ancestor_asg" | "testapp-asg-v000"
  }

  void "should allow target capacity to be percentage based"() {
    setup:
    def config = [cluster : "testapp-asg", target: "current_asg", regions: ["us-west-1", "us-east-1"],
                  scalePct: 50, credentials: "test"]
    def pipeline = new Pipeline()
    def stage = new PipelineStage(pipeline, "resizeAsg", config)

    when:
    stageBuilder.buildSteps(stage)

    then:
    1 * oort.getCluster("testapp", "test", "testapp-asg", "aws") >> {
      def cluster = [serverGroups: [[
                                      name  : "testapp-asg-v001",
                                      region: "us-west-1",
                                      asg   : [
                                        minSize        : 10,
                                        maxSize        : 10,
                                        desiredCapacity: 10
                                      ]
                                    ]]]
      new Response(
        "foo", 200, "ok", [],
        new TypedByteArray(
          "application/json",
          mapper.writeValueAsBytes(cluster)
        )
      )
    }
    stage.context.capacity == [min: 15, max: 15, desired: 15]
  }

  void "should allow target capacity to be incrementally scaled"() {
    setup:
    def config = [cluster : "testapp-asg", target: "current_asg", regions: ["us-west-1", "us-east-1"],
                  scaleNum: 5, credentials: "test"]
    def pipeline = new Pipeline()
    def stage = new PipelineStage(pipeline, "resizeAsg", config)

    when:
    stageBuilder.buildSteps(stage)

    then:
    1 * oort.getCluster("testapp", "test", "testapp-asg", "aws") >> {
      def cluster = [serverGroups: [[
                                      name  : "testapp-asg-v001",
                                      region: "us-west-1",
                                      asg   : [
                                        minSize        : 10,
                                        maxSize        : 10,
                                        desiredCapacity: 10
                                      ]
                                    ]]]
      new Response(
        "foo", 200, "ok", [],
        new TypedByteArray(
          "application/json",
          mapper.writeValueAsBytes(cluster)
        )
      )
    }
    stage.context.capacity == [min: 15, max: 15, desired: 15]
  }

  void "should scale percentage factors up"() {
    setup:
    def config = [cluster : "testapp-asg", target: "current_asg", regions: ["us-west-1", "us-east-1"],
                  scalePct: 50, credentials: "test"]
    def pipeline = new Pipeline()
    def stage = new PipelineStage(pipeline, "resizeAsg", config)

    when:
    stageBuilder.buildSteps(stage)

    then:
    1 * oort.getCluster("testapp", "test", "testapp-asg", "aws") >> {
      def cluster = [serverGroups: [[
                                      name  : "testapp-asg-v001",
                                      region: "us-west-1",
                                      asg   : [
                                        minSize        : 5,
                                        maxSize        : 5,
                                        desiredCapacity: 5
                                      ]
                                    ]]]
      new Response(
        "foo", 200, "ok", [],
        new TypedByteArray(
          "application/json",
          mapper.writeValueAsBytes(cluster)
        )
      )
    }
    stage.context.capacity == [min: 8, max: 8, desired: 8]
  }

  void "should be able to derive scaling direction from inputs"() {
    setup:
    def config = [cluster : "testapp-asg", target: "current_asg", regions: ["us-west-1", "us-east-1"],
                  scalePct: 50, credentials: "test", action: action]
    def pipeline = new Pipeline()
    def stage = new PipelineStage(pipeline, "resizeAsg", config)

    when:
    stageBuilder.buildSteps(stage)

    then:
    1 * oort.getCluster("testapp", "test", "testapp-asg", "aws") >> {
      def cluster = [serverGroups: [[
                                      name  : "testapp-asg-v001",
                                      region: "us-west-1",
                                      asg   : [
                                        minSize        : 5,
                                        maxSize        : 5,
                                        desiredCapacity: 5
                                      ]
                                    ]]]
      new Response(
        "foo", 200, "ok", [],
        new TypedByteArray(
          "application/json",
          mapper.writeValueAsBytes(cluster)
        )
      )
    }
    stage.context.capacity == capacity

    where:
    action       | capacity
    "scale_up"   | [min: 8, max: 8, desired: 8]
    "scale_down" | [min: 2, max: 2, desired: 2]
  }
}
