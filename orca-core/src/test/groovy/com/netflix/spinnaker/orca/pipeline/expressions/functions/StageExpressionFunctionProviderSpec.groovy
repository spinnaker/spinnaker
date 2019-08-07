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

import com.netflix.spinnaker.kork.expressions.SpelHelperFunctionException
import com.netflix.spinnaker.orca.ExecutionContext
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class StageExpressionFunctionProviderSpec extends Specification {
  @Shared
  def pipeline = pipeline {
    stage {
      id = "1"
      refId = "1.0"
      name = "My First Stage"
      context = [
        "region": "us-east-1",
      ]
    }

    stage {
      id = "2"
      refId = "2.0"
      name = "My Second Stage"
      context = [
        "region": "us-west-1",
      ]
    }

    stage {
      id = "3"
      refId = "3"
      status = SUCCEEDED
      type = "createServerGroup"
      name = "Deploy in us-east-1"
      context.putAll(
        "account": "test",
        "deploy.account.name": "test",
        "availabilityZones": [
          "us-east-1": [
            "us-east-1c",
            "us-east-1d",
            "us-east-1e"
          ]
        ],
        "capacity": [
          "desired": 1,
          "max"    : 1,
          "min"    : 1
        ],
        "deploy.server.groups": [
          "us-east-1": [
            "app-test-v001"
          ]
        ]
      )
    }

    stage {
      id = "4"
      refId = "4"
      status = SUCCEEDED
      type = "disableServerGroup"
      name = "disable server group"
      context.putAll(
        "account": "test",
        "deploy.account.name": "test",
        "availabilityZones": [
          "us-east-1": [
            "us-east-1c",
            "us-east-1d",
            "us-east-1e"
          ]
        ],
        "capacity": [
          "desired": 1,
          "max"    : 1,
          "min"    : 1
        ],
        "deploy.server.groups": [
          "us-west-2": [
            "app-test-v002"
          ]
        ]
      )
    }
  }

  @Unroll
  def "should resolve current stage"() {
    given:
    ExecutionContext.set(
        new ExecutionContext(
            null, null, null, null, currentStageId, null, null
        )
    )

    when:
    def currentStage = StageExpressionFunctionProvider.currentStage(pipeline)

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
    StageExpressionFunctionProvider.currentStage(pipeline)

    then:
    thrown(SpelHelperFunctionException)

    where:
    executionContext << [
        new ExecutionContext(
            null, null, null, null, "-1", null, null
        ),
        null
    ]
  }

  def "stageByRefId() should match on #matchedAttribute"() {
    expect:
    StageExpressionFunctionProvider.stageByRefId(pipeline, stageCriteria).name == expectedStageName

    where:
    stageCriteria || matchedAttribute || expectedStageName
    "1.0"         || "refId"          || "My First Stage"
    "2.0"         || "refId"          || "My Second Stage"
  }

  def "stageByRefId() should raise exception if stage not found"() {
    when:
    StageExpressionFunctionProvider.stageByRefId(pipeline, "does_not_exist")

    then:
    thrown(SpelHelperFunctionException)
  }

  @Unroll
  def "stage() should match on #matchedAttribute"() {
    expect:
    StageExpressionFunctionProvider.stage(pipeline, stageCriteria).context.region == expectedRegion

    where:
    stageCriteria     || matchedAttribute || expectedRegion
    "My Second Stage" || "name"           || "us-west-1"
    "1"               || "id"             || "us-east-1"
    "2"               || "id"             || "us-west-1"
  }

  def "stage() should raise exception if stage not found"() {
    when:
    StageExpressionFunctionProvider.stage(pipeline, "does_not_exist")

    then:
    thrown(SpelHelperFunctionException)
  }

  def "stageExists() returns whether a stage exists or not"() {
    when:
    def exists = StageExpressionFunctionProvider.stageExists(pipeline, id)

    then:
    exists == real

    where:
    id                   | real
    "My First Stage"     | true
    "1"                  | true
    "My Second Stage"    | true
    "2"                  | true
    "Non-existent Stage" | false
    "42"                 | false
  }
}
