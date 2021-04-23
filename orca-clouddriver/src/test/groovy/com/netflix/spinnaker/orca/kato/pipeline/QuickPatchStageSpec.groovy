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

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.ModelUtils
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilderImpl
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import spock.lang.Specification
import spock.lang.Subject

class QuickPatchStageSpec extends Specification {

  def oortHelper = Mock(OortHelper)
  def bulkQuickPatchStage = new BulkQuickPatchStage()

  @Subject
    quickPatchStage = new QuickPatchStage(oortHelper: oortHelper, bulkQuickPatchStage: bulkQuickPatchStage)

  def "no-ops if there are no instances"() {
    given:
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "quickPatch", context)
    def graphBefore = StageGraphBuilderImpl.beforeStages(stage)
    def graphAfter = StageGraphBuilderImpl.afterStages(stage)

    oortHelper.getInstancesForCluster(_, null, true) >> [:]

    when:
    quickPatchStage.beforeStages(stage, graphBefore)
    quickPatchStage.afterStages(stage, graphAfter)

    then:
    stage.status == ExecutionStatus.SUCCEEDED
    graphBefore.build().isEmpty()
    graphAfter.build().isEmpty()

    where:
    context = [:]
  }

  def "quick patch can't run due to too many asgs"() {
    given:
    def config = [
      application: "deck",
      clusterName: "deck-cluster",
      account    : "account",
      region     : "us-east-1",
      baseOs     : "ubuntu"
    ]
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "quickPatch", config)
    def graphBefore = StageGraphBuilderImpl.beforeStages(stage)
    def graphAfter = StageGraphBuilderImpl.afterStages(stage)

    oortHelper.getInstancesForCluster(config, null, true) >> {
      throw new RuntimeException("too many asgs!")
    }

    when:
    quickPatchStage.beforeStages(stage, graphBefore)
    quickPatchStage.afterStages(stage, graphAfter)

    then:
    thrown(RuntimeException)

    where:
    asgNames = ["deck-prestaging-v300", "deck-prestaging-v303", "deck-prestaging-v304"]
  }

  def "configures bulk quickpatch"() {
    given:
    def config = [
      application: "deck",
      clusterName: "deck-cluster",
      account    : "account",
      region     : "us-east-1",
      baseOs     : "ubuntu"
    ]
    oortHelper.getInstancesForCluster(config, null, true) >> expectedInstances
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "quickPatch", config)
    def graphBefore = StageGraphBuilderImpl.beforeStages(stage)
    def graphAfter = StageGraphBuilderImpl.afterStages(stage)
    def syntheticStages = []

    when:
    quickPatchStage.beforeStages(stage, graphBefore)
    quickPatchStage.afterStages(stage, graphAfter)

    syntheticStages.addAll(graphBefore.build())
    syntheticStages.addAll(graphAfter.build())

    then:
    syntheticStages.size() == 1
    syntheticStages*.type == [bulkQuickPatchStage.type]

    and:
    with(syntheticStages[0].context) {
      application == "deck"
      account == "account"
      region == "us-east-1"
      clusterName == "deck-cluster"
      instanceIds == ["i-1234", "i-2345"]
      instances.size() == expectedInstances.size()
      instances.every {
        it.value.hostName == expectedInstances.get(it.key).hostName
        it.value.healthCheckUrl == expectedInstances.get(it.key).healthCheckUrl
      }
    }

    where:
    asgNames = ["deck-prestaging-v300"]
    instance1 = [instanceId: "i-1234", publicDnsName: "foo.com", health: [[foo: "bar"], [healthCheckUrl: "http://foo.com:7001/healthCheck"]]]
    instance2 = [instanceId: "i-2345", publicDnsName: "foo2.com", health: [[foo2: "bar"], [healthCheckUrl: "http://foo2.com:7001/healthCheck"]]]
    expectedInstances = ["i-1234": ModelUtils.instanceInfo([hostName: "foo.com", healthCheckUrl: "http://foo.com:7001/healthCheck"]),
                         "i-2345": ModelUtils.instanceInfo([hostName: "foo2.com", healthCheckUrl: "http://foo.com:7001/healthCheck"])]
  }

  def "configures rolling quickpatch"() {
    given:
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "quickPatch", config)
    def graphAfter = StageGraphBuilderImpl.afterStages(stage)
    def syntheticStages = []

    when:
    quickPatchStage.afterStages(stage, graphAfter)
    syntheticStages.addAll(graphAfter.build())

    then:
    1 * oortHelper.getInstancesForCluster(config, null, true) >> expectedInstances

    and:
    syntheticStages.size() == 2

    and:
    syntheticStages*.type.unique() == [bulkQuickPatchStage.type]

    and:
    with(syntheticStages[0].context) {
      application == "deck"
      account == "account"
      region == "us-east-1"
      clusterName == config.clusterName
      instanceIds == ["i-1234"]
      instances.size() == 1
      instances.every {
        it.value.hostName == expectedInstances.get(it.key).hostName
        it.value.healthCheckUrl == expectedInstances.get(it.key).healthCheckUrl
      }
    }

    and:
    with(syntheticStages[1].context) {
      application == "deck"
      account == "account"
      region == "us-east-1"
      clusterName == config.clusterName
      instanceIds == ["i-2345"]
      instances.size() == 1
      instances.every {
        it.value.hostName == expectedInstances.get(it.key).hostName
        it.value.healthCheckUrl == expectedInstances.get(it.key).healthCheckUrl
      }
    }

    where:
    asgNames = ["deck-prestaging-v300"]
    instance1 = [instanceId: "i-1234", publicDnsName: "foo.com", health: [[foo: "bar"], [healthCheckUrl: "http://foo.com:7001/healthCheck"]]]
    instance2 = [instanceId: "i-2345", publicDnsName: "foo2.com", health: [[foo2: "bar"], [healthCheckUrl: "http://foo2.com:7001/healthCheck"]]]
    expectedInstances = ["i-1234": ModelUtils.instanceInfo([hostName: "foo.com", healthCheckUrl: "http://foo.com:7001/healthCheck"]),
                         "i-2345": ModelUtils.instanceInfo([hostName: "foo2.com", healthCheckUrl: "http://foo.com:7001/healthCheck"])]

    config | _
    [application: "deck", clusterName: "deck-cluster", account: "account", region: "us-east-1", rollingPatch: true, baseOs: "ubuntu"] | _
    [application: "deck", clusterName: "deck", account: "account", region: "us-east-1", rollingPatch: true, baseOs: "ubuntu"] | _
  }
}
