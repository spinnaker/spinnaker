/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
import spock.lang.Subject
import spock.lang.Unroll

class CopyLastAsgStageSpec extends Specification {

  @Subject copyLastAsgStage = new CopyLastAsgStage()
  def oort = Mock(OortService)
  def disableAsgStage = Mock(DisableAsgStage)
  def destroyAsgStage = Mock(DestroyAsgStage)

  def objectMapper = new OrcaObjectMapper()
  def pipelineStore = new InMemoryPipelineStore(objectMapper)
  def orchestrationStore = new InMemoryOrchestrationStore(objectMapper)
  def executionRepository = new DefaultExecutionRepository(orchestrationStore, pipelineStore)

  void setup() {
    copyLastAsgStage.applicationContext = Stub(ApplicationContext)
    copyLastAsgStage.mapper = objectMapper
    copyLastAsgStage.steps = new StepBuilderFactory(Stub(JobRepository), Stub(PlatformTransactionManager))
    copyLastAsgStage.taskTaskletAdapter = new TaskTaskletAdapter(executionRepository, [])
    copyLastAsgStage.oort = oort
    copyLastAsgStage.disableAsgStage = disableAsgStage
    copyLastAsgStage.destroyAsgStage = destroyAsgStage
  }

  @Unroll
  def "configures destroy ASG tasks for all pre-existing clusters if strategy is #strategy"() {
    given:
    def config = [
        source  : [
            account: account,
            asgName: asgNames.last(),
            region : region
        ],
        strategy: strategy
    ]

    and:
    def stage = new PipelineStage(null, "copyLastAsg", config)

    when:
    copyLastAsgStage.buildSteps(stage)

    then:
    1 * oort.getCluster("deck", account, "deck-prestaging", "aws") >> {
      def responseBody = [
          serverGroups: asgNames.collect { name ->
            [name: name, region: region]
          }
      ]
      new Response(
          "foo", 200, "ok", [],
          new TypedByteArray(
              "application/json",
              objectMapper.writeValueAsBytes(responseBody)
          )
      )
    }

    and:
    3 * destroyAsgStage.buildSteps(_) >> []

    and:
    stage.context.destroyAsgDescriptions == asgNames.collect { name ->
      [asgName: name, credentials: account, regions: [region]]
    }

    where:
    strategy = "highlander"
    asgNames = ["deck-prestaging-v300", "deck-prestaging-v303", "deck-prestaging-v304"]
    region = "us-east-1"
    account = "prod"
  }

  def "configures disable ASG task for last cluster if strategy is redblack"() {
    given:
    def config = [
        source  : [
            account: account,
            asgName: asgNames.last(),
            region : region
        ],
        strategy: strategy
    ]

    and:
    def stage = new PipelineStage(null, "copyLastAsg", config)

    when:
    copyLastAsgStage.buildSteps(stage)

    then:
    1 * oort.getCluster("deck", account, "deck-prestaging", "aws") >> {
      def responseBody = [
          serverGroups: asgNames.collect { name ->
            [name: name, region: region]
          }
      ]
      new Response(
          "foo", 200, "ok", [],
          new TypedByteArray(
              "application/json",
              objectMapper.writeValueAsBytes(responseBody)
          )
      )
    }

    and:
    1 * disableAsgStage.buildSteps(_) >> []

    and:
    stage.context.disableAsg == [
        asgName    : asgNames.last(),
        credentials: account,
        regions    : [region]
    ]

    where:
    strategy = "redblack"
    asgNames = ["deck-prestaging-v300", "deck-prestaging-v303", "deck-prestaging-v304"]
    region = "us-east-1"
    account = "prod"
  }

  def "doesn't configure any cleanup steps if no strategy is specified"() {
    given:
    def config = [
        source  : [
            account: account,
            asgName: asgName,
            region : region
        ],
        strategy: strategy
    ]

    and:
    def stage = new PipelineStage(null, "copyLastAsg", config)

    when:
    copyLastAsgStage.buildSteps(stage)

    then:
    0 * oort._

    and:
    0 * destroyAsgStage.buildSteps(_)
    0 * disableAsgStage.buildSteps(_)

    where:
    strategy = ""
    asgName = "deck-prestaging-v304"
    region = "us-east-1"
    account = "prod"
  }

}
