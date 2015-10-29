/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.kato.pipeline.strategy

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.orca.batch.TaskTaskletAdapter
import com.netflix.spinnaker.orca.clouddriver.pipeline.DisableClusterStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.ScaleDownClusterStage
import com.netflix.spinnaker.orca.clouddriver.pipeline.ShrinkClusterStage
import com.netflix.spinnaker.orca.kato.pipeline.CopyLastAsgStage
import com.netflix.spinnaker.orca.kato.pipeline.NeverClearedArrayList
import com.netflix.spinnaker.orca.kato.pipeline.support.SourceResolver
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.persistence.jedis.JedisExecutionRepository
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.repository.JobRepository
import org.springframework.context.ApplicationContext
import org.springframework.transaction.PlatformTransactionManager
import redis.clients.jedis.Jedis
import redis.clients.util.Pool
import spock.lang.*

class DeployStrategyStageSpec extends Specification {
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

  @Subject
    copyLastAsgStage = new CopyLastAsgStage()
  def resolver = Mock(SourceResolver)
  def shrinkClusterStage = Mock(ShrinkClusterStage)
  def scaleDownClusterStage = Mock(ScaleDownClusterStage)
  def disableClusterStage = Mock(DisableClusterStage)

  def executionRepository = new JedisExecutionRepository(new NoopRegistry(), jedisPool, 1, 50)

  void setup() {
    copyLastAsgStage.applicationContext = Stub(ApplicationContext) {
      getBean(_) >> { Class type -> type.newInstance() }
    }
    copyLastAsgStage.steps = new StepBuilderFactory(Stub(JobRepository), Stub(PlatformTransactionManager))
    copyLastAsgStage.taskTaskletAdapter = new TaskTaskletAdapter(executionRepository, [])
    copyLastAsgStage.shrinkClusterStage = shrinkClusterStage
    copyLastAsgStage.scaleDownClusterStage = scaleDownClusterStage
    copyLastAsgStage.disableClusterStage = disableClusterStage
  }

  @Unroll
  def "configures shrinkCluster stage ASG if strategy is #strategy"() {
    given:
    def config = [
      application      : "deck",
      availabilityZones: [(region): []],
      stack            : stack,
      account          : account,
      cloudProvider    : 'aws',
      source           : [
        account: account,
        asgName: cluster + '-v001',
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
    1 == stage.afterStages.size()

    and:
    stage.afterStages[0].stageBuilder == shrinkClusterStage

    and:
    stage.afterStages[0].context ==
      [credentials : account, regions: [region], cluster: cluster, cloudProvider: 'aws',
       shrinkToSize: 1, retainLargerOverNewer: false, allowDeleteActive: true]


    where:
    strategy = "highlander"
    stack = "main"
    region = "us-east-1"
    account = "prod"
    cluster = "deck-$stack".toString()
  }

  def "configures disableServerGroup if strategy is redblack"() {
    given:
    def config = [
      application      : "deck",
      availabilityZones: [(region): []],
      stack            : stack,
      account          : account,
      source           : [
        account: account,
        asgName: cluster + '-v001',
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
    1 == stage.afterStages.size()

    and:
    stage.afterStages[0].stageBuilder == disableClusterStage

    and:
    stage.afterStages[0].context == [credentials                 : account,
                                     cloudProvider               : 'aws',
                                     cluster                     : cluster,
                                     regions                     : [region],
                                     remainingEnabledServerGroups: 1,
                                     preferLargerOverNewer       : false,
    ]

    where:
    strategy = "redblack"
    stack = "main"
    region = "us-east-1"
    account = "prod"
    cluster = "deck-main"
  }

  @Unroll
  def "configures shrinkCluster task before disableServerGroup if maxRemainingAsgs is defined"() {
    given:
    def config = [
      application      : "deck",
      availabilityZones: [(region): []],
      stack            : "main",
      account          : account,
      source           : [
        account: account,
        asgName: 'deck-main-v001',
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

    (hasShrinkCluster ? 2 : 1) == stage.afterStages.size()

    and:
    int index = 0
    if (hasShrinkCluster) {
      stage.afterStages[0].stageBuilder == shrinkClusterStage
      stage.afterStages[0].context == [shrinkToSize: maxRemainingAsgs, allowDeleteActive: true, preferLargerOverNewer: false]
      index++
    }
    stage.afterStages[index].stageBuilder == disableClusterStage

    where:
    maxRemainingAsgs << [null, -1, 0, 1, 2, 3]
    region = 'us-east-1'
    account = 'prod'
    hasShrinkCluster = maxRemainingAsgs != null && maxRemainingAsgs > 0
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

  @Unroll
  void "should include freeFormDetails when building cluster name"() {
    given:
    def stage = new PipelineStage(
      new Pipeline(),
      "deploy",
      [
        application    : application,
        stack          : stack,
        freeFormDetails: freeFormDetails
      ]
    )

    expect:
    stage.mapTo(StageData).getCluster() == cluster

    where:
    application | stack        | freeFormDetails || cluster
    "myapp"     | "prestaging" | "freeform"      || "myapp-prestaging-freeform"
    "myapp"     | "prestaging" | null            || "myapp-prestaging"
    "myapp"     | null         | "freeform"      || "myapp--freeform"
    "myapp"     | null         | null            || "myapp"
  }

  @Unroll
  void "stage data should favor account over credentials"() {
    given:
    def stage = new PipelineStage(
      new Pipeline(),
      "deploy",
      [
        account    : account,
        credentials: credentials
      ]
    )

    when:
    def mappedAccount
    try {
      mappedAccount = stage.mapTo(StageData).getAccount()
    } catch (Exception e) {
      mappedAccount = e.class.simpleName
    }

    then:
    mappedAccount == expectedAccount

    where:
    account | credentials || expectedAccount
    "test"  | "prod"      || "IllegalStateException"
    "test"  | null        || "test"
    null    | "test"      || "test"
  }
}
