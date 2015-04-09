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
import com.netflix.spinnaker.orca.kato.pipeline.support.TargetReference
import com.netflix.spinnaker.orca.kato.pipeline.support.TargetReferenceSupport
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.persistence.DefaultExecutionRepository
import com.netflix.spinnaker.orca.pipeline.persistence.memory.InMemoryOrchestrationStore
import com.netflix.spinnaker.orca.pipeline.persistence.memory.InMemoryPipelineStore
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.repository.JobRepository
import org.springframework.context.ApplicationContext
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.Specification

class ResizeAsgStageSpec extends Specification {

  def mapper = OrcaObjectMapper.DEFAULT
  def targetReferenceSupport = Mock(TargetReferenceSupport)
  def stageBuilder = new ResizeAsgStage(targetReferenceSupport: targetReferenceSupport)
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
    1 * targetReferenceSupport.getTargetAsgReferences(stage) >> asgs.collect {
      new TargetReference(region: it.region, asg: it)
    }

    stage.beforeStages.collect { it.context } == asgs.collect{[
        asgName: it.name, credentials: 'test', regions: [it.region], action: 'resume', processes: ['Launch', 'Terminate']
    ]}
    stage.afterStages.collect { it.context } == [config] + asgs.collect{[
      asgName: it.name, credentials: 'test', regions: [it.region], action: 'suspend', processes: ['Launch', 'Terminate']
    ]}

    where:
    asgs = [[
              name  : "testapp-asg-v000",
              region: "us-west-1",
              asg   : [
                minSize           : 5,
                maxSize           : 5,
                desiredCapacity   : 5,
                suspendedProcesses: [
                  [processName: 'Terminate'], [processName: 'Launch']
                ]
              ]
            ], [
              name  : "testapp-asg-v000",
              region: "us-east-1",
              asg   : [
                minSize           : 5,
                maxSize           : 5,
                desiredCapacity   : 5,
                suspendedProcesses: [
                  [processName: 'Terminate'], [processName: 'Launch']
                ]
              ]
            ]]
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
    1 * targetReferenceSupport.getTargetAsgReferences(stage) >> [targetRef]

    stage.afterStages.size() == 1
    stage.afterStages[0].context.asgName == asgName

    where:
    target         | asgName            | targetRef
    "current_asg"  | "testapp-asg-v001" | new TargetReference(region: "us-west-1", asg: [
      name  : "testapp-asg-v001",
      region: "us-west-1",
      asg   : [
        minSize        : 5,
        maxSize        : 5,
        desiredCapacity: 5
      ]
    ])
    "ancestor_asg" | "testapp-asg-v000" | new TargetReference(region: "us-west-1", asg: [
      name  : "testapp-asg-v000",
      region: "us-west-1",
      asg   : [
        minSize        : 5,
        maxSize        : 5,
        desiredCapacity: 5
      ]
    ])

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
    1 * targetReferenceSupport.getTargetAsgReferences(stage) >> [new TargetReference(region: "us-west-1", asg: [
      name  : "testapp-asg-v001",
      region: "us-west-1",
      asg   : [
        minSize        : 10,
        maxSize        : 10,
        desiredCapacity: 10
      ]
    ])]

    stage.afterStages.size() == 1
    stage.afterStages[0].context.capacity == [min: 15, max: 15, desired: 15] as Map
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
    1 * targetReferenceSupport.getTargetAsgReferences(stage) >> [new TargetReference(region: "us-west-1", asg: [
      name  : "testapp-asg-v001",
      region: "us-west-1",
      asg   : [
        minSize        : 10,
        maxSize        : 10,
        desiredCapacity: 10
      ]
    ])]

    stage.afterStages.size() == 1
    stage.afterStages[0].context.capacity == [min: 15, max: 15, desired: 15] as Map
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
    1 * targetReferenceSupport.getTargetAsgReferences(stage) >> [new TargetReference(region: "us-west-1", asg: [
      name  : "testapp-asg-v001",
      region: "us-west-1",
      asg   : [
        minSize        : 5,
        maxSize        : 5,
        desiredCapacity: 5
      ]
    ])]

    stage.afterStages.size() == 1
    stage.afterStages[0].context.capacity == [min: 8, max: 8, desired: 8] as Map
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
    1 * targetReferenceSupport.getTargetAsgReferences(stage) >> [new TargetReference(region: "us-west-1", asg: [
      name  : "testapp-asg-v001",
      region: "us-west-1",
      asg   : [
        minSize        : 5,
        maxSize        : 5,
        desiredCapacity: 5
      ]
    ])]

    stage.afterStages.size() == 1
    stage.afterStages[0].context.capacity == capacity

    where:
    action       | capacity
    "scale_up"   | [min: 8, max: 8, desired: 8]
    "scale_down" | [min: 2, max: 2, desired: 2]
  }
}
