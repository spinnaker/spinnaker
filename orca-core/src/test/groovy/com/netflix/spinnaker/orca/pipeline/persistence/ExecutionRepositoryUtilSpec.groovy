/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.orca.pipeline.persistence

import spock.lang.Specification

import static com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_AFTER
import static com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.orchestration
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class ExecutionRepositoryUtilSpec extends Specification {

  def "should order stages by ref id"() {
    given:
    def one = stage {
      type = "three"
      refId = "3"
    }
    def two = stage {
      type = "one"
      refId = "1"
    }
    def three = stage {
      type = "two"
      refId = "2"
    }
    def execution = orchestration {}

    when:
    ExecutionRepositoryUtil.sortStagesByReference(execution, [three, one, two])

    then:
    execution.stages*.refId == ["1", "2", "3"]
  }

  def "should order stages by synthetic owner"() {
    given:
    def execution = orchestration {
      stage {
        id = "01CGD1S7EBTG7A0T26RTXTF06J"
        type = "findImage"
        refId = "1"
      }
      stage {
        id = "01CGD1S7EBSVYR6SAPQVP75MD9"
        type = "deploy"
        refId = "2"
        requisiteStageRefIds = ["1"]
      }
      stage {
        id = "01CGD1S80ZRGAPTRNYS91W7470"
        type = "createServerGroup"
        refId = "2<1"
        syntheticStageOwner = STAGE_BEFORE
        parentStageId = "01CGD1S7EBSVYR6SAPQVP75MD9"
      }
      stage {
        id = "01CGD1VVW36VNSGTK06Y4JDVK5"
        type = "applySourceServerGroupCapacity"
        refId = "2<1>2"
        syntheticStageOwner = STAGE_AFTER
        requisiteStageRefIds = ["2<1>1"]
        parentStageId = "01CGD1S80ZRGAPTRNYS91W7470"
      }
      stage {
        id = "01CGD201G3KYDVDA2296JZWA2A"
        type = "deleteEntityTags"
        refId = "2<1>2>1"
        syntheticStageOwner = STAGE_AFTER
        parentStageId = "01CGD1VVW36VNSGTK06Y4JDVK5"
      }
      stage {
        id = "01CGD1VVYD146H2YW29ZF3WQRJ"
        type = "disableCluster"
        refId = "2<1>1<1"
        syntheticStageOwner = STAGE_BEFORE
        parentStageId = "01CGD1VVW22YPFS1QATAR53SB2"
      }
      stage {
        id = "01CGD1VVW22YPFS1QATAR53SB2"
        type = "shrinkCluster"
        refId = "2<1>1"
        syntheticStageOwner = STAGE_AFTER
        parentStageId = "01CGD1S80ZRGAPTRNYS91W7470"
      }
    }

    def stages = new ArrayList(execution.stages)
    execution.stages.clear()

    when:
    ExecutionRepositoryUtil.sortStagesByReference(execution, stages)

    then:
    execution.stages*.refId == ["1", "2<1", "2<1>2", "2<1>2>1", "2<1>1<1", "2<1>1", "2"]
  }
}
