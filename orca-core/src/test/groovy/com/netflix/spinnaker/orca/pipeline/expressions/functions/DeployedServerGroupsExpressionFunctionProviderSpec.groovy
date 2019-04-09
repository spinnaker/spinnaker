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

import spock.lang.Shared
import spock.lang.Specification

import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import static com.netflix.spinnaker.orca.pipeline.expressions.functions.DeployedServerGroupsExpressionFunctionProvider.deployedServerGroups
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class DeployedServerGroupsExpressionFunctionProviderSpec extends Specification {
  @Shared
  def pipeline = pipeline {
    stage {
      id = "1"
      name = "My First Stage"
      context = [
        "region": "us-east-1",
      ]
    }

    stage {
      id = "2"
      name = "My Second Stage"
      context = [
        "region": "us-west-1",
      ]
    }

    stage {
      id = "3"
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

  def "deployedServerGroup should resolve for valid stage type"() {
    when:
    def map = deployedServerGroups(pipeline)

    then: "(deploy|createServerGroup|cloneServerGroup|rollingPush)"
    map.serverGroup == ["app-test-v001"]
  }

  def "deployedServerGroup should resolve deployments for valid stage type"() {

    final pipelineWithDeployments = pipeline {
      stage {
        id = "1"
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
              "app-test-v001",
              "app-test-v002",
              "app-test-v003"
            ]
          ],
          "kato.tasks": [
            [
              "resultObjects": [
                [
                  "deployments": [
                    [
                      "serverGroupName": "app-test-v001"
                    ]
                  ],
                  "serverGroupNames": [
                    "us-east-1:app-test-v001"
                  ]
                ]
              ]
            ],
            [
              "resultObjects": [
                [
                  "deployments": [
                    [
                      "serverGroupName": "app-test-v002"
                    ],
                    [
                      "serverGroupName": "app-test-v003"
                    ]
                  ]
                ]
              ]
            ],
            [
              "resultObjects": [ [:] ]
            ],
            [:]
          ]
        )
      }
    }

    when:
    def map = deployedServerGroups(pipelineWithDeployments)

    then:
    map.serverGroup == ["app-test-v001"]
    map.deployments == [[
      [ "serverGroupName": "app-test-v001" ],
      [ "serverGroupName": "app-test-v002" ],
      [ "serverGroupName": "app-test-v003" ],
    ]]
  }
}
