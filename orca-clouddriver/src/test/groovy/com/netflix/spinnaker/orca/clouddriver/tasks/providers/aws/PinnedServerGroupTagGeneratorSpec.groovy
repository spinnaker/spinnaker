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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws

import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.PinnedServerGroupTagGenerator
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll;

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class PinnedServerGroupTagGeneratorSpec extends Specification {
  @Subject
  def pinnedServerGroupTagGenerator = new PinnedServerGroupTagGenerator()

  @Unroll
  def "should not generate a tag if pinned capacity (min) and original capacity (min) are the same"() {
    given:
    def stage = stage {
      context = [
        capacity                         : capacity,
        sourceServerGroupCapacitySnapshot: sourceServerGroupCapacitySnapshot
      ]
    }

    when:
    def tags = pinnedServerGroupTagGenerator.generateTags(stage, "app-stack-details-v001", "test", "us-east-1", "aws")

    then:
    tags.isEmpty()

    where:
    capacity                     | sourceServerGroupCapacitySnapshot || _
    [min: 2, max: 5, desired: 3] | null                              || _
    null                         | [min: 2, max: 5, desired: 3]      || _
    [min: 2, max: 5, desired: 3] | [min: 2, max: 5, desired: 3]      || _
  }

  def "should generate a tag when original 'min' capacity and pinned 'min' capacity differ"() {
    given:
    def stage = stage {
      context = [
        capacity                         : [min: 3, max: 5, desired: 3],
        sourceServerGroupCapacitySnapshot: [min: 2, max: 5, desired: 3]
      ]
    }

    when:
    def tags = pinnedServerGroupTagGenerator.generateTags(stage, "app-stack-details-v001", "test", "us-east-1", "aws")

    then:
    tags == [
      [
        name : "spinnaker:pinned_capacity",
        value: [
          serverGroup: "app-stack-details-v001",
          account: "test",
          location: "us-east-1",
          cloudProvider: "aws",
          executionId: stage.execution.id,
          executionType: stage.execution.type,
          stageId: stage.id,
          pinnedCapacity: [min: 3, max: 5, desired: 3],
          unpinnedCapacity: [min: 2, max: 5, desired: 3]
        ]
      ]
    ]
  }
}
