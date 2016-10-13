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

import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.kato.pipeline.support.ResizeSupport
import com.netflix.spinnaker.orca.kato.pipeline.support.TargetReference
import com.netflix.spinnaker.orca.kato.pipeline.support.TargetReferenceSupport
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.persistence.jedis.JedisExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.StageNavigator
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.repository.JobRepository
import org.springframework.context.ApplicationContext
import org.springframework.transaction.PlatformTransactionManager
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner
import redis.clients.jedis.Jedis
import redis.clients.util.Pool
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ResizeAsgStageSpec extends Specification {

  @Shared
  def stageNavigator = new StageNavigator(Mock(ApplicationContext))

  @Shared
  @AutoCleanup("destroy")
  EmbeddedRedis embeddedRedis

  def setupSpec() {
    embeddedRedis = EmbeddedRedis.embed()
  }

  def cleanup() {
    embeddedRedis.jedis.withCloseable { it.flushDB() }
  }

  Pool<Jedis> jedisPool = embeddedRedis.pool

  def mapper = OrcaObjectMapper.DEFAULT
  def targetReferenceSupport = Mock(TargetReferenceSupport)
  def resizeSupport = new ResizeSupport(targetReferenceSupport: targetReferenceSupport)
  def stageBuilder = new ResizeAsgStage()

  def setup() {
    stageBuilder.modifyScalingProcessStage = new ModifyScalingProcessStage()
    stageBuilder.determineTargetReferenceStage = new DetermineTargetReferenceStage()
    stageBuilder.targetReferenceSupport = targetReferenceSupport
    stageBuilder.resizeSupport = resizeSupport
  }

  void "should create basic stage according to inputs"() {
    setup:
    def config = [asgName    : "testapp-asg-v000", regions: ["us-west-1", "us-east-1"], capacity: [min: 0, max: 0, desired: 0],
                  credentials: "test"]
    def stage = new PipelineStage(new Pipeline(), "resizeAsg", config)

    when:
    stageBuilder.buildTaskGraph(stage)

    then:
    stage.status == ExecutionStatus.SUCCEEDED

    when:
    def syntheticStages = stageBuilder.aroundStages(stage)
    def beforeStages = syntheticStages.findAll { it.syntheticStageOwner == SyntheticStageOwner.STAGE_BEFORE }
    def afterStages = syntheticStages.findAll { it.syntheticStageOwner == SyntheticStageOwner.STAGE_AFTER }

    then:
    1 * targetReferenceSupport.getTargetAsgReferences(stage) >> asgs.collect {
      new TargetReference(region: it.region, asg: it)
    }

    beforeStages.collect { it.context } == asgs.collect {
      [
        asgName: it.name, credentials: 'test', regions: [it.region], action: 'resume', processes: ['Launch', 'Terminate']
      ]
    }
    afterStages.collect { it.context } == [config] + asgs.collect {
      [
        asgName: it.name, credentials: 'test', regions: [it.region], action: 'suspend'
      ]
    }

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
    def stage = new PipelineStage(new Pipeline(), "resizeAsg", config)

    when:
    def syntheticStages = stageBuilder.aroundStages(stage)
    def beforeStages = syntheticStages.findAll { it.syntheticStageOwner == SyntheticStageOwner.STAGE_BEFORE }
    def afterStages = syntheticStages.findAll { it.syntheticStageOwner == SyntheticStageOwner.STAGE_AFTER }

    then:
    1 * targetReferenceSupport.getTargetAsgReferences(stage) >> [targetRef]

    afterStages.size() == 2
    afterStages[0].context.asgName == asgName
    afterStages*.name == ["resizeAsg", "suspendScalingProcesses"]
    beforeStages*.name == ["resumeScalingProcesses"]

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

  void "should inject a determineTargetReferences stage if target is dynamic"() {
    setup:
    def config = [cluster : "testapp-asg", target: target, regions: ["us-east-1"],
                  capacity: [min: 0, max: 0, desired: 0], credentials: "test"]
    def stage = new PipelineStage(new Pipeline(), "resizeAsg", config)

    when:
    def syntheticStages = stageBuilder.aroundStages(stage)
    def beforeStages = syntheticStages.findAll { it.syntheticStageOwner == SyntheticStageOwner.STAGE_BEFORE }
    def afterStages = syntheticStages.findAll { it.syntheticStageOwner == SyntheticStageOwner.STAGE_AFTER }

    then:
    _ * targetReferenceSupport.isDynamicallyBound(_) >> true
    1 * targetReferenceSupport.getTargetAsgReferences(stage) >> [new TargetReference(region: "us-east-1",
      cluster: "testapp-asg")]

    afterStages.size() == 2
    beforeStages.size() == 2
    afterStages*.name == ["resizeAsg", "suspendScalingProcesses"]
    beforeStages*.name == ['resumeScalingProcesses', 'determineTargetReferences']

    where:
    target << ['ancestor_asg_dynamic', 'current_asg_dynamic']
  }

}
