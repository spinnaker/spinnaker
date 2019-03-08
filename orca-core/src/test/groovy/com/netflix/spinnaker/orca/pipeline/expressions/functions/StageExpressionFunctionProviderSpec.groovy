/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline.expressions.functions

import com.netflix.spinnaker.orca.ExecutionContext
import com.netflix.spinnaker.orca.pipeline.expressions.SpelHelperFunctionException
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.pipeline.expressions.functions.StageExpressionFunctionProvider.currentStage
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage;

class StageExpressionFunctionProviderSpec extends Specification {
  @Shared
  def pipeline = pipeline {
    stage {
      id = "1"
      name = "My First Stage"
    }

    stage {
      id = "2"
      name = "My Second Stage"
    }
  }

  @Unroll
  def "should resolve current stage"() {
    given:
    ExecutionContext.set(
        new ExecutionContext(
            null, null, null, null, currentStageId, null
        )
    )

    when:
    def currentStage = currentStage(pipeline)

    then:
    currentStage.name == expectedStageName

    where:
    currentStageId || expectedStageName
    "1"            || "My First Stage"
    "2"            || "My Second Stage"
  }

  @Unroll
  def "should raise exception if current stage cannot be found"() {
    given:
    ExecutionContext.set(
        executionContext
    )

    when:
    currentStage(pipeline)

    then:
    thrown(SpelHelperFunctionException)

    where:
    executionContext << [
        new ExecutionContext(
            null, null, null, null, "-1", null
        ),
        null
    ]
  }
}
