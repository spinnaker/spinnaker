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

package com.netflix.spinnaker.orca.kato.pipeline

import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.batch.TaskTaskletAdapter
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.oort.OortService
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.DefaultExecutionRepository
import com.netflix.spinnaker.orca.pipeline.persistence.memory.InMemoryOrchestrationStore
import com.netflix.spinnaker.orca.pipeline.persistence.memory.InMemoryPipelineStore
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.repository.JobRepository
import org.springframework.context.ApplicationContext
import org.springframework.transaction.PlatformTransactionManager
import retrofit.client.Response
import retrofit.mime.TypedByteArray
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class DeployStageSpec extends Specification {

  def configJson = """\
          {
            "type":"deploy",
            "cluster":{
                "strategy":"IMPLEMENTED_IN_SPEC",
                "application":"pond",
                "stack":"prestaging",
                "instanceType":"m3.medium",
                "securityGroups":[
                  "nf-infrastructure-vpc",
                  "nf-datacenter-vpc"
                ],
                "subnetType":"internal",
                "availabilityZones":{
                  "us-west-1":[

                  ]
                },
                "capacity":{
                  "min":1,
                  "max":1,
                  "desired":1
                },
                "loadBalancers":[
                  "pond-prestaging-frontend"
                ]
            },
            "account":"prod"
          }
  """.stripIndent()

  def mapper = new OrcaObjectMapper()
  def objectMapper = new OrcaObjectMapper()
  def pipelineStore = new InMemoryPipelineStore(objectMapper)
  def orchestrationStore = new InMemoryOrchestrationStore(objectMapper)
  def executionRepository = new DefaultExecutionRepository(orchestrationStore, pipelineStore)

  @Subject DeployStage deployStage

  @Shared OortService oortService
  @Shared DisableAsgStage disableAsgStage
  @Shared DestroyAsgStage destroyAsgStage
  @Shared ResizeAsgStage resizeAsgStage
  @Shared ShrinkClusterStage shrinkClusterStage
  @Shared ModifyScalingProcessStage modifyScalingProcessStage

  def setup() {
    oortService = Mock(OortService)
    disableAsgStage = Mock(DisableAsgStage)
    destroyAsgStage = Mock(DestroyAsgStage)
    resizeAsgStage = Mock(ResizeAsgStage)
    shrinkClusterStage = Mock(ShrinkClusterStage)
    modifyScalingProcessStage = Mock(ModifyScalingProcessStage)

    deployStage = new DeployStage(oort: oortService, disableAsgStage: disableAsgStage, destroyAsgStage: destroyAsgStage,
        resizeAsgStage: resizeAsgStage, shrinkClusterStage: shrinkClusterStage,
        modifyScalingProcessStage: modifyScalingProcessStage, mapper: mapper)
    deployStage.steps = new StepBuilderFactory(Stub(JobRepository), Stub(PlatformTransactionManager))
    deployStage.taskTaskletAdapter = new TaskTaskletAdapter(executionRepository, [])
    deployStage.applicationContext = Stub(ApplicationContext) {
      getBean(_) >> { Class type -> type.newInstance() }
    }
  }

  void "should create stages for deploy and disableAsg when strategy is redblack"() {
    setup:
    def pipeline = new Pipeline()
    def config = mapper.readValue(configJson, Map)
    config.cluster.strategy = "redblack"
    def stage = new PipelineStage(pipeline, config.remove("type") as String, config)
    stage.beforeStages = new NeverClearedArrayList()
    stage.afterStages = new NeverClearedArrayList()

    when:
    deployStage.buildSteps(stage)

    then:
    "should call to oort to get the last ASG so that we know what to disable"
    1 * oortService.getCluster(config.cluster.application, config.account, "pond-prestaging", "aws") >> {
      def cluster = [serverGroups: [[name: "pond-prestaging-v000", region: "us-west-1"]]]
      new Response("foo", 200, "ok", [], new TypedByteArray("application/json", objectMapper.writeValueAsBytes(cluster)))
    }
    1 == stage.afterStages.size()
    stage.afterStages[0].stageBuilder == disableAsgStage
  }

  void "should choose the ancestor asg from the same region when redblack is selected"() {
    setup:
    def pipeline = new Pipeline()
    def config = mapper.readValue(configJson, Map)
    config.cluster.strategy = "redblack"
    def stage = new PipelineStage(pipeline, config.remove("type") as String, config)
    stage.beforeStages = new NeverClearedArrayList()
    stage.afterStages = new NeverClearedArrayList()

    when:
    deployStage.buildSteps(stage)

    then:
    "should call to oort to get the last ASG so that we know what to disable"
    1 * oortService.getCluster(config.cluster.application, config.account, "pond-prestaging", "aws") >> {
      def cluster = [serverGroups: [[name: "pond-prestaging-v000", region: "us-east-1"],
                                    [name: "pond-prestaging-v000", region: "us-west-1"]]]
      new Response("foo", 200, "ok", [], new TypedByteArray("application/json", objectMapper.writeValueAsBytes(cluster)))
    }
    stage.afterStages[0].context.regions == config.availabilityZones.keySet().toList()
  }

  void "should create stages of deploy, resizeAsg, disableAsg, and enableTerminate when strategy is redblack and scaleDown is true"() {
    setup:
    def pipeline = new Pipeline()
    def config = mapper.readValue(configJson, Map)
    config.cluster.scaleDown = true
    config.cluster.strategy = "redblack"
    def stage = new PipelineStage(pipeline, config.remove("type") as String, config)
    stage.beforeStages = new NeverClearedArrayList()
    stage.afterStages = new NeverClearedArrayList()

    when:
    deployStage.buildSteps(stage)

    then:
    "should call to oort to get the last ASG so that we know what to disable"
    1 * oortService.getCluster(config.cluster.application, config.account, "pond-prestaging", "aws") >> {
      def cluster = [serverGroups: [[
                                        name  : "pond-prestaging-v000",
                                        region: "us-west-1"
                                    ]]]
      new Response(
          "foo", 200, "ok", [],
          new TypedByteArray(
              "application/json",
              objectMapper.writeValueAsBytes(cluster)
          )
      )
    }
    3 == stage.afterStages.size()
    stage.afterStages*.stageBuilder == [resizeAsgStage, disableAsgStage, modifyScalingProcessStage]
    stage.afterStages[2].context.action == "resume"
    stage.afterStages[2].context.processes == ["Terminate"]
  }

  void "should create stages of deploy, resizeAsg, disableAsg, enableTerminate, and shrinkCluster stages when strategy is redblack and scaleDown is true and shrinkCluster is true"() {
    setup:
    def pipeline = new Pipeline()
    def config = mapper.readValue(configJson, Map)
    config.cluster.scaleDown = true
    config.cluster.strategy = "redblack"
    config.cluster.shrinkCluster = true
    def stage = new PipelineStage(pipeline, config.remove("type") as String, config)
    stage.beforeStages = new NeverClearedArrayList()
    stage.afterStages = new NeverClearedArrayList()

    when:
    deployStage.buildSteps(stage)

    then:
    "should call to oort to get the last ASG so that we know what to disable"
    1 * oortService.getCluster(config.cluster.application, config.account, "pond-prestaging", "aws") >> {
      def cluster = [serverGroups: [[
                                      name  : "pond-prestaging-v000",
                                      region: "us-west-1"
                                    ]]]
      new Response(
        "foo", 200, "ok", [],
        new TypedByteArray(
          "application/json",
          objectMapper.writeValueAsBytes(cluster)
        )
      )
    }
    4 == stage.afterStages.size()
    stage.afterStages*.stageBuilder == [resizeAsgStage, disableAsgStage, modifyScalingProcessStage, shrinkClusterStage]
  }

  void "should create stages of deploy and destroyAsg when strategy is highlander"() {
    setup:
    def pipeline = new Pipeline()
    def config = mapper.readValue(configJson, Map)
    config.cluster.strategy = "highlander"
    def stage = new PipelineStage(pipeline, config.remove("type") as String, config)
    stage.beforeStages = new NeverClearedArrayList()
    stage.afterStages = new NeverClearedArrayList()

    when:
    deployStage.buildSteps(stage)

    then:
    "should call to oort to get the last ASG so that we know what to disable"
    1 * oortService.getCluster(config.cluster.application, config.account, "pond-prestaging", "aws") >> {
      def cluster = [serverGroups: [[
                                        name  : "pond-prestaging-v000",
                                        region: "us-west-1"
                                    ]]]
      new Response(
          "foo", 200, "ok", [],
          new TypedByteArray(
              "application/json",
              objectMapper.writeValueAsBytes(cluster)
          )
      )
    }
    1 == stage.afterStages.size()
    stage.afterStages[0].stageBuilder == destroyAsgStage
  }

  void "should create basicDeploy tasks when no strategy is chosen"() {
    setup:
    def pipeline = new Pipeline()
    def config = mapper.readValue(configJson, Map)
    def stage = new PipelineStage(pipeline, config.remove("type") as String, config)

    when:
    def steps = deployStage.buildSteps(stage)

    then:
    steps*.name.collect {
      it.tokenize('.')[1]
    } == deployStage.basicSteps(stage)*.name.collect { it.tokenize('.')[1] }
  }
}
