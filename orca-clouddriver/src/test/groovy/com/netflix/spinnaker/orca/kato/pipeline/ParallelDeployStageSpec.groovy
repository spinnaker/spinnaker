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

package com.netflix.spinnaker.orca.kato.pipeline

import com.netflix.spinnaker.orca.pipeline.model.JenkinsTrigger
import com.netflix.spinnaker.orca.pipeline.model.PipelineTrigger
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class ParallelDeployStageSpec extends Specification {

  @Unroll
  def "should build contexts corresponding to cluster configuration(s)"() {
    given:
    def pipeline = pipeline {
      trigger = new JenkinsTrigger("master", "job", 1, null)
      application = "orca"
    }
    def bakeStage = new Stage(pipeline, "deploy", "Deploy!", stageContext)
    def builder = new ParallelDeployStage()

    when:
    def parallelContexts = builder.parallelContexts(bakeStage)

    then:
    parallelContexts == expectedParallelContexts

    where:
    stageContext                                                         || expectedParallelContexts
    deployStageContext("prod", null, "us-west-1")                        || [[account: "prod", restrictedExecutionWindow: [:], cluster: [availabilityZones: ["us-west-1": []]], type: "createServerGroup", name: "Deploy in us-west-1"]]
    deployStageContext("prod", null, "us-west-1", "us-east-1")           || [[account: "prod", restrictedExecutionWindow: [:], cluster: [availabilityZones: ["us-west-1": []]], type: "createServerGroup", name: "Deploy in us-west-1"],
                                                                             [account: "prod", restrictedExecutionWindow: [:], cluster: [availabilityZones: ["us-east-1": []]], type: "createServerGroup", name: "Deploy in us-east-1"]]
    [availabilityZones: ["us-west-1": []], account: "prod"]              || [[account: "prod", cluster: [availabilityZones: ["us-west-1": []], account: "prod"], type: "createServerGroup", name: "Deploy in us-west-1"]]
    deployStageContext("prod", "gce", "us-central1-a")                   || [[account: "prod", restrictedExecutionWindow: [:], cluster: [availabilityZones: ["us-central1-a": []], cloudProvider: "gce"], type: "createServerGroup", name: "Deploy in us-central1-a"]]
    deployStageContext("prod", "gce", "us-central1-a", "europe-west1-b") || [[account: "prod", restrictedExecutionWindow: [:], cluster: [availabilityZones: ["us-central1-a": []], cloudProvider: "gce"], type: "createServerGroup", name: "Deploy in us-central1-a"],
                                                                             [account: "prod", restrictedExecutionWindow: [:], cluster: [availabilityZones: ["europe-west1-b": []], cloudProvider: "gce"], type: "createServerGroup", name: "Deploy in europe-west1-b"]]
  }

  @Unroll
  def "pipeline strategy should #data.scenario"() {
    given:
    def parentPipeline = pipeline {
      trigger = new JenkinsTrigger("master", "job", 1, null)
      application = "orca"
      stage {
        name = "parent stage"
        type = "createServerGroup"
        refId = "parentStage"
        context = [
          account                  : "prod",
          availabilityZones        : ["us-west-1": []],
          cloudProvider            : "aws",
          restrictedExecutionWindow: [:]
        ].with { putAll(data.parentStageContext); it }
      }
    }

    def strategyPipeline = pipeline {
      trigger = new PipelineTrigger(
        "pipeline",
        null,
        "example@example.com",
        data.triggerParams.with {
          putAll([strategy: true, parentStageId: parentPipeline.stageByRef("parentStage").id]);
          it
        },
        [],
        [],
        false,
        false,
        true,
        parentPipeline,
        parentPipeline.stageByRef("parentStage").id
      )
    }

    and:
    def deployStage = new Stage(strategyPipeline, "deploy", "Deploy!", data.stageContext)
    def builder = new ParallelDeployStage()

    when:
    def parallelContexts = builder.parallelContexts(deployStage)

    then:
    parallelContexts == data.expectedParallelContexts

    where:
    data << [
      [
        scenario                : "pull contexts from trigger when missing from parent",
        parentStageContext      : [:],
        stageContext            : [:],
        triggerParams           : [
          amiName: "ami-1234"
        ],
        expectedParallelContexts: [
          [
            name   : "Deploy in us-west-1",
            cluster: [
              amiName                  : "ami-1234",
              restrictedExecutionWindow: [:],
              cloudProvider            : "aws",
              availabilityZones        : ["us-west-1": []],
              strategy                 : "none",
              account                  : "prod"
            ],
            account: "prod",
            type   : "createServerGroup",
          ]
        ]
      ],
      [
        scenario                : "inherit traffic options from parent",
        parentStageContext      : [
          amiName           : "ami-1234",
          suspendedProcesses: ['AddToLoadBalancer'],
        ],
        stageContext            : [
          trafficOptions: "inherit"
        ],
        triggerParams           : [:],
        expectedParallelContexts: [
          [
            name          : "Deploy in us-west-1",
            cluster       : [
              amiName                  : "ami-1234",
              restrictedExecutionWindow: [:],
              cloudProvider            : "aws",
              availabilityZones        : ["us-west-1": []],
              suspendedProcesses       : ['AddToLoadBalancer'],
              strategy                 : "none",
              account                  : "prod"
            ],
            type          : "createServerGroup",
            trafficOptions: "inherit",
            account       : "prod"
          ]
        ]
      ],
      [
        scenario                : "override traffic options in parent to enable cluster",
        parentStageContext      : [
          amiName           : "ami-1234",
          suspendedProcesses: ['AddToLoadBalancer'],
        ],
        stageContext            : [
          trafficOptions: "enable"
        ],
        triggerParams           : [:],
        expectedParallelContexts: [
          [
            name          : "Deploy in us-west-1",
            cluster       : [
              amiName                  : "ami-1234",
              restrictedExecutionWindow: [:],
              cloudProvider            : "aws",
              availabilityZones        : ["us-west-1": []],
              suspendedProcesses       : [],
              strategy                 : "none",
              account                  : "prod"
            ],
            type          : "createServerGroup",
            trafficOptions: "enable",
            account       : "prod"
          ]
        ]
      ],
      [
        scenario                : "override traffic options in parent to disable cluster",
        parentStageContext      : [
          amiName           : "ami-1234",
          suspendedProcesses: [],
        ],
        stageContext            : [
          trafficOptions: "disable"
        ],
        triggerParams           : [:],
        expectedParallelContexts: [
          [
            name          : "Deploy in us-west-1",
            cluster: [
              amiName                  : "ami-1234",
              restrictedExecutionWindow: [:],
              cloudProvider            : "aws",
              availabilityZones        : ["us-west-1": []],
              suspendedProcesses       : ['AddToLoadBalancer'],
              strategy                 : "none",
              account                  : "prod"
            ],
            type          : "createServerGroup",
            trafficOptions: "disable",
            account: "prod"
          ]
        ]
      ]
    ]
  }

  Map deployStageContext(String account, String cloudProvider, String... availabilityZones) {
    def context = ["account": account, restrictedExecutionWindow: [:]]
    if (availabilityZones.size() == 1) {
      context.cluster = ["availabilityZones": [(availabilityZones[0]): []]]

      if (cloudProvider) {
        context.cluster.cloudProvider = cloudProvider
      }
    } else {
      context.clusters = availabilityZones.collect {
        ["availabilityZones": [(it): []]]
      }

      if (cloudProvider) {
        context.clusters.each {
          it.cloudProvider = cloudProvider
        }
      }
    }
    return context
  }
}
