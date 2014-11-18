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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.batch.StageStatusPropagationListener
import com.netflix.spinnaker.orca.batch.TaskTaskletAdapter
import com.netflix.spinnaker.orca.oort.OortService
import com.netflix.spinnaker.orca.pipeline.model.ImmutableStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.memory.InMemoryPipelineStore
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.repository.JobRepository
import org.springframework.context.ApplicationContext
import org.springframework.transaction.PlatformTransactionManager
import retrofit.client.Response
import retrofit.mime.TypedInput
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

  def mapper = new ObjectMapper()
  def pipelineStore = new InMemoryPipelineStore()

  @Subject DeployStage deployStage

  @Shared OortService oortService
  @Shared DisableAsgStage disableAsgStage
  @Shared DestroyAsgStage destroyAsgStage

  def setup() {
    oortService = Mock(OortService)
    disableAsgStage = Mock(DisableAsgStage)
    destroyAsgStage = Mock(DestroyAsgStage)

    deployStage = new DeployStage(oort: oortService, disableAsgStage: disableAsgStage, destroyAsgStage: destroyAsgStage,
      mapper: mapper)
    deployStage.steps = new StepBuilderFactory(Stub(JobRepository), Stub(PlatformTransactionManager))
    deployStage.taskTaskletAdapter = new TaskTaskletAdapter(pipelineStore)
    deployStage.stageStatusPropagationListener = new StageStatusPropagationListener(pipelineStore)
    deployStage.applicationContext = Stub(ApplicationContext)
  }

  void "should create tasks of basicDeploy and disableAsg when strategy is redblack"() {
    setup:
    def config = mapper.readValue(configJson, Map)
    config.cluster.strategy = "redblack"
    def stage = new Stage(config.remove("type"), config)
    def disableAsgTask = deployStage.buildStep("foo", TestTask)

    when:
    def steps = deployStage.buildSteps(stage)

    then:
    "should call to oort to get the last ASG so that we know what to disable"
    1 * oortService.getCluster(config.cluster.application, config.account, "pond-prestaging") >> {
      def cluster = [serverGroups: [[
                                      name  : "pond-prestaging-v000",
                                      region: "us-east-1"
                                    ]]]
      def typedInput = Stub(TypedInput)
      typedInput.in() >> new ByteArrayInputStream(mapper.writeValueAsString(cluster).bytes)
      def response = new Response("foo", 200, "ok", [], typedInput)
      response
    }
    1 * disableAsgStage.buildSteps(stage) >> [disableAsgTask]
    steps[-1] == disableAsgTask
  }

  void "should create tasks of basicDeploy and destroyAsg when strategy is highlander"() {
    setup:
    def config = mapper.readValue(configJson, Map)
    config.cluster.strategy = "highlander"
    def stage = new Stage(config.remove("type"), config)
    def destroyAsgTask = deployStage.buildStep("foo", TestTask)

    when:
    def steps = deployStage.buildSteps(stage)

    then:
    "should call to oort to get the last ASG so that we know what to disable"
    stage.context.containsKey("destroyAsgDescriptions")
    1 == stage.context.destroyAsgDescriptions.size()
    1 * oortService.getCluster(config.cluster.application, config.account, "pond-prestaging") >> {
      def cluster = [serverGroups: [[
                                      name  : "pond-prestaging-v000",
                                      region: "us-east-1"
                                    ]]]
      def typedInput = Stub(TypedInput)
      typedInput.in() >> new ByteArrayInputStream(mapper.writeValueAsString(cluster).bytes)
      def response = new Response("foo", 200, "ok", [], typedInput)
      response
    }
    1 * destroyAsgStage.buildSteps(stage) >> [destroyAsgTask]
    steps[-1] == destroyAsgTask
  }

  void "should create basicDeploy tasks when no strategy is chosen"() {
    setup:
    def config = mapper.readValue(configJson, Map)
    def stage = new Stage(config.remove("type"), config)

    when:
    def steps = deployStage.buildSteps(stage)

    then:
    steps*.name.collect {
      it.tokenize('.')[1]
    } == deployStage.basicStages()*.name.collect { it.tokenize('.')[1] }
  }

  static class TestTask implements Task {

    @Override
    TaskResult execute(ImmutableStage stage) {
      return null
    }
  }
}
