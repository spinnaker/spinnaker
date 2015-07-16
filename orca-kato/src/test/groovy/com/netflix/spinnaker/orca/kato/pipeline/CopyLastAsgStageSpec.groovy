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
import com.netflix.spinnaker.orca.kato.pipeline.support.SourceResolver
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.persistence.memory.InMemoryExecutionRepository
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.repository.JobRepository
import org.springframework.context.ApplicationContext
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class CopyLastAsgStageSpec extends Specification {

  @Subject copyLastAsgStage = new CopyLastAsgStage()
  def resolver = Mock(SourceResolver)
  def disableAsgStage = Mock(DisableAsgStage)
  def destroyAsgStage = Mock(DestroyAsgStage)

  def objectMapper = new OrcaObjectMapper()
  def executionRepository = new InMemoryExecutionRepository()

  void setup() {
    copyLastAsgStage.applicationContext = Stub(ApplicationContext) {
      getBean(_) >> { Class type -> type.newInstance() }
    }
    copyLastAsgStage.mapper = objectMapper
    copyLastAsgStage.steps = new StepBuilderFactory(Stub(JobRepository), Stub(PlatformTransactionManager))
    copyLastAsgStage.taskTaskletAdapter = new TaskTaskletAdapter(executionRepository, [])
    copyLastAsgStage.sourceResolver = resolver
    copyLastAsgStage.disableAsgStage = disableAsgStage
    copyLastAsgStage.destroyAsgStage = destroyAsgStage
  }

  @Unroll
  def "configures destroy ASG tasks for all pre-existing clusters if strategy is #strategy"() {
    given:
    def config = [
      application      : "deck",
      availabilityZones: [(region): []],
      stack            : stack,
      account          : account,
      source           : [
        account: account,
        asgName: asgNames.last(),
        region : region
      ],
      strategy         : strategy
    ]

    and:
    def stage = new PipelineStage(null, "copyLastAsg", config)
    stage.beforeStages = new NeverClearedArrayList()
    stage.afterStages = new NeverClearedArrayList()

    when:
    copyLastAsgStage.buildSteps(stage)

    then:
    1 * resolver.getExistingAsgs("deck", account, "deck-${stack}", "aws") >> {
      asgNames.collect { name ->
        [name: name, region: region]
      }
    }

    and:
    3 == stage.afterStages.size()

    and:
    stage.afterStages*.stageBuilder.unique() == [destroyAsgStage]

    and:
    stage.afterStages*.context == asgNames.collect { name ->
      [asgName: name, credentials: account, regions: [region]]
    }

    where:
    strategy = "highlander"
    stack = "main"
    asgNames = ["deck-prestaging-v300", "deck-prestaging-v303", "deck-prestaging-v304"]
    region = "us-east-1"
    account = "prod"
  }

  def "configures disable ASG task for last cluster if strategy is redblack"() {
    given:
    def config = [
      application      : "deck",
      availabilityZones: [(region): []],
      stack            : stack,
      account          : account,
      source           : [
        account: account,
        asgName: asgNames.last(),
        region : region
      ],
      strategy         : strategy
    ]

    and:
    def stage = new PipelineStage(null, "copyLastAsg", config)
    stage.beforeStages = new NeverClearedArrayList()
    stage.afterStages = new NeverClearedArrayList()

    when:
    copyLastAsgStage.buildSteps(stage)

    then:
    1 * resolver.getExistingAsgs("deck", account, "deck-${stack}", "aws") >> {
      asgNames.collect { name ->
        [name: name, region: region]
      }
    }

    and:
    1 == stage.afterStages.size()

    and:
    stage.afterStages[0].stageBuilder == disableAsgStage

    and:
    stage.afterStages[0].context == [asgName    : asgNames.sort().reverse().first(),
                                     credentials: account,
                                     regions    : [region]]

    where:
    strategy = "redblack"
    stack = "main"
    asgNames = ["deck-prestaging-v300", "deck-prestaging-v303", "deck-prestaging-v304"]
    region = "us-east-1"
    account = "prod"
  }

  @Unroll
  def "configures destroy ASG task #calledDestroyAsgNumTimes times if maxRemainingAsgs is defined"() {
    given:
    def config = [
      application      : "deck",
      availabilityZones: [(region): []],
      stack            : "main",
      account          : account,
      source           : [
        account: account,
        asgName: asgNames.last(),
        region : region
      ],
      strategy         : "redblack",
      maxRemainingAsgs : maxRemainingAsgs
    ]

    and:
    def stage = new PipelineStage(null, "copyLastAsg", config)
    stage.beforeStages = new NeverClearedArrayList()
    stage.afterStages = new NeverClearedArrayList()

    when:
    copyLastAsgStage.buildSteps(stage)

    then:
    1 * resolver.getExistingAsgs("deck", account, "deck-main", "aws") >> {
      asgNames.collect { name ->
        [name: name, region: region]
      }
    }

    and:
    calledDestroyAsgNumTimes + 1 == stage.afterStages.size()

    and:
    if (calledDestroyAsgNumTimes > 0) {
      def index = 0
      stage.afterStages[1..calledDestroyAsgNumTimes].context.every { it ->
        it == [asgName: asgNames.get(index++), regions: [region.toString()], credentials: account.toString()]
      }
      stage.afterStages[1..calledDestroyAsgNumTimes].stageBuilder.every { it ->
        it == destroyAsgStage
      }
    }

    where:
    asgNames                                                                 | region      | account | maxRemainingAsgs | calledDestroyAsgNumTimes
    ["deck-prestaging-v300", "deck-prestaging-v303", "deck-prestaging-v304"] | "us-east-1" | "prod"  | 3                | 1
    ["deck-prestaging-v300", "deck-prestaging-v303", "deck-prestaging-v304"] | "us-east-1" | "prod"  | 2                | 2
    ["deck-prestaging-v300", "deck-prestaging-v303", "deck-prestaging-v304"] | "us-east-1" | "prod"  | 1                | 3
    ["deck-prestaging-v300", "deck-prestaging-v303", "deck-prestaging-v304"] | "us-east-1" | "prod"  | 0                | 0
    ["deck-prestaging-v300", "deck-prestaging-v303", "deck-prestaging-v304"] | "us-east-1" | "prod"  | -1               | 0

    ["deck-prestaging-v300"]                                                 | "us-east-1" | "prod"  | 0                | 0
    ["deck-prestaging-v300"]                                                 | "us-east-1" | "prod"  | 1                | 1
    ["deck-prestaging-v300"]                                                 | "us-east-1" | "prod"  | 2                | 0

    ["deck-prestaging-v300", "deck-prestaging-v303", "deck-prestaging-v304"] | "us-east-1" | "prod"  | 4                | 0
    ["deck-prestaging-v300", "deck-prestaging-v303", "deck-prestaging-v304"] | "us-east-1" | "prod"  | 5                | 0
  }

  def "doesn't configure any cleanup steps if no strategy is specified"() {
    given:
    def config = [
      application      : "deck",
      availabilityZones: [(region): []],
      source           : [
        account: account,
        asgName: asgName,
        region : region
      ],
      strategy         : strategy
    ]

    and:
    def stage = new PipelineStage(null, "copyLastAsg", config)
    stage.beforeStages = new NeverClearedArrayList()
    stage.afterStages = new NeverClearedArrayList()

    when:
    copyLastAsgStage.buildSteps(stage)

    then:
    0 * resolver.getExistingAsgs(*_)

    and:
    0 == stage.afterStages.size()

    where:
    strategy = ""
    asgName = "deck-prestaging-v304"
    region = "us-east-1"
    account = "prod"
  }

  def "should only highlander ASGs in target region"() {
    given:
    def stage = new PipelineStage(
      new Pipeline(),
      "copyLastAsg",
      [
        "application"      : application,
        "availabilityZones": [(region): ["${region}a", "${region}b", "${region}c"].collect { it.toString() }],
        "stack"            : stack,
        "account"          : targetAccount,
        "source"           : [
          "account": sourceAccount,
          "region" : region,
          "asgName": asgName
        ]
      ]
    )

    when:
    copyLastAsgStage.composeHighlanderFlow(stage)

    then:
    1 * resolver.getExistingAsgs(application, targetAccount, "${application}-${stack}", "aws") >> {
      ["us-east-1", "us-west-1", "us-west-2", "eu-west-1"].collect {
        [region: it, name: asgName]
      }
    }

    and:
    stage.afterStages.size() == 1
    stage.afterStages[0].context["asgName"] == asgName
    stage.afterStages[0].context["regions"] == [region]

    where:
    sourceAccount = "test"
    targetAccount = "prod"
    stack = "main"
    region = "us-west-2"
    application = "myapp"
    asgName = "${application}-test-v000" as String
  }
}
