/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws

import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.orca.clouddriver.FeaturesService
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.pipeline.entitytags.DeleteEntityTagsStage
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilder
import spock.lang.Specification
import spock.lang.Subject
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class ApplySourceServerGroupCapacityStageSpec extends Specification {
  def featuresService = Mock(FeaturesService)
  def oortService = Mock(OortService)
  def retrySupport = Spy(RetrySupport) {
    _ * sleep(_) >> { /* do nothing */ }
  }

  def stage = stage {
    context = [
      serverGroupName       : "app-stack-details-v001",
      credentials           : "test",
      "deploy.server.groups": ["us-east-1": ["app-stack-details-v001"]]
    ]
  }

  @Subject
  def stageBuilder = new ApplySourceServerGroupCapacityStage(
    featuresService: featuresService,
    oortService: oortService,
    retrySupport: retrySupport,
    deleteEntityTagsStage: new DeleteEntityTagsStage()
  )

  def "should not generate any stages when 'upsertEntityTags' is not enabled"() {
    when:
    def graph = StageGraphBuilder.afterStages(stage)
    stageBuilder.afterStages(stage, graph)
    def afterStages = graph.build()

    then:
    1 * featuresService.areEntityTagsAvailable() >> { return false }
    0 * oortService.getEntityTags(_)

    afterStages.isEmpty()
  }

  def "should not generate any stages when there are no entity tags"() {
    when:
    def graph = StageGraphBuilder.afterStages(stage)
    stageBuilder.afterStages(stage, graph)
    def afterStages = graph.build()

    then:
    1 * featuresService.areEntityTagsAvailable() >> { return true }
    1 * oortService.getEntityTags(_) >> { return [] }

    afterStages.isEmpty()
  }

  def "should not generate any stages when an exception is raised"() {
    when:
    def graph = StageGraphBuilder.afterStages(stage)
    stageBuilder.afterStages(stage, graph)
    def afterStages = graph.build()

    then:
    notThrown(RuntimeException)

    1 * featuresService.areEntityTagsAvailable() >> { throw new RuntimeException("An Exception!") }
    0 * oortService.getEntityTags(_)

    afterStages.isEmpty()
  }

  def "should generate a delete entity tags stage when the server group has a `spinnaker:pinned_capacity` entity tag"() {
    when:
    def graph = StageGraphBuilder.afterStages(stage)
    stageBuilder.afterStages(stage, graph)
    def afterStages = graph.build()

    then:
    1 * featuresService.areEntityTagsAvailable() >> { return true }
    1 * oortService.getEntityTags([
      "tag:spinnaker:pinned_capacity": "*",
      "entityId"                     : "app-stack-details-v001",
      "account"                      : "test",
      "region"                       : "us-east-1"
    ]) >> {
      return [
        [id: "my-entity-tags-id"]
      ]
    }

    afterStages.size() == 1
    afterStages[0].context == [
      id  : "my-entity-tags-id",
      tags: ["spinnaker:pinned_capacity"]
    ]
  }
}
