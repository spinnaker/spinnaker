/*
 * Copyright 2019 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.front50.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO
import com.netflix.spinnaker.front50.model.pipeline.PipelineTemplate
import com.netflix.spinnaker.front50.model.pipeline.PipelineTemplateDAO
import spock.lang.Specification
import spock.lang.Subject

class V2PipelineTemplateControllerSpec extends Specification {
  def pipelineDAO = Mock(PipelineDAO)
  def pipelineTemplateDAO = Mock(PipelineTemplateDAO)

  @Subject
  def controller = new V2PipelineTemplateController(
    pipelineDAO: pipelineDAO,
    pipelineTemplateDAO: pipelineTemplateDAO,
    objectMapper: new ObjectMapper(),
  )

  def template1 = [
    "id": "nuTemplate2",
    "lastModifiedBy": "anonymous",
    "metadata": [
      "description": "A generic application wait and wait pipeline.",
      "name": "Default Wait and Wait",
      "owner": "example@example.com",
      "scopes": [
        "global"
      ]
    ],
    "pipeline": [
      "description": "",
      "keepWaitingPipelines": false,
      "lastModifiedBy": "anonymous",
      "limitConcurrent": true,
      "notifications": [],
      "parameterConfig": [],
      "stages": [
        [
          "name": "My Wait Stage",
          "requisiteStageRefIds": [],
          "refId": "wait1",
          "type": "wait",
          "waitTime": '${ templateVariables.waitTime }'
        ],
        "deeerp"
      ],
      "triggers": [
        [
          "attributeConstraints": [],
          "enabled": true,
          "payloadConstraints": [],
          "pubsubSystem": "google",
          "source": "jake",
          "subscription": "super-why",
          "subscriptionName": "super-why",
          "type": "pubsub"
        ]
      ],
    ],
    "protect": false,
    "schema": "v2",
    "variables": [
      [
        "description": "The time a wait stage shall pauseth",
        "defaultValue": 43,
        "name": "waitTime",
        "type": "int"
      ]
    ]
  ]

  def template2 = [
    "variables": [
      [
        "defaultValue": 43,
        "description": "The time a wait stage shall pauseth",
        "name": "waitTime",
        "type": "int"
      ]
    ],
    "lastModifiedBy": "anonymous",
    "metadata": [
      "description": "A generic application wait and wait pipeline.",
      "name": "Default Wait and Wait",
      "owner": "example@example.com",
      "scopes": [
        "global"
      ]
    ],
    "pipeline": [
      "description": "",
      "keepWaitingPipelines": false,
      "lastModifiedBy": "anonymous",
      "limitConcurrent": true,
      "notifications": [],
      "parameterConfig": [],
      "stages": [
        [
          "name": "My Wait Stage",
          "refId": "wait1",
          "requisiteStageRefIds": [],
          "type": "wait",
          "waitTime": '${ templateVariables.waitTime }'
        ],
        "deeerp"
      ],
      "triggers": [
        [
          "attributeConstraints": [],
          "enabled": true,
          "payloadConstraints": [],
          "pubsubSystem": "google",
          "source": "jake",
          "subscription": "super-why",
          "subscriptionName": "super-why",
          "type": "pubsub"
        ]
      ],
    ],
    "protect": false,
    "schema": "v2",
    "id": "nuTemplate2",
  ]

  def "should calculate the same digest given two maps with identical keys regardless of order"() {
    given:
    def instaTemplate1 = new PipelineTemplate(template1)
    def instaTemplate2 = new PipelineTemplate(template2)

    when:
    def hash1 = controller.computeSHA256Digest(instaTemplate1)
    def hash2 = controller.computeSHA256Digest(instaTemplate2)

    then:
    hash1 == hash2
  }
}

