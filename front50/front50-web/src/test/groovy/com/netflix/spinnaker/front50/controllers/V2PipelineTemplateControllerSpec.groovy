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

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.front50.exceptions.InvalidEntityException
import static com.netflix.spinnaker.front50.api.model.pipeline.Pipeline.TYPE_TEMPLATED;
import static com.netflix.spinnaker.front50.model.pipeline.TemplateConfiguration.TemplateSource.SPINNAKER_PREFIX;
import com.netflix.spinnaker.front50.api.model.pipeline.Pipeline;
import com.netflix.spinnaker.front50.model.pipeline.V2TemplateConfiguration;
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO
import com.netflix.spinnaker.front50.model.pipeline.PipelineTemplate
import com.netflix.spinnaker.front50.model.pipeline.PipelineTemplateDAO
import com.netflix.spinnaker.front50.model.pipeline.TemplateConfiguration
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class V2PipelineTemplateControllerSpec extends Specification {
  def pipelineDAO = Mock(PipelineDAO)
  def pipelineTemplateDAO = Mock(PipelineTemplateDAO)

  @Subject
  def controller = new V2PipelineTemplateController(
    pipelineDAO: pipelineDAO,
    pipelineTemplateDAO: pipelineTemplateDAO,
    // V2TemplateConfiguration doesn't expect `id` attribute as part of pipeline config.
    // Hence has to ignore unknown properties when converting the value.
    objectMapper: new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
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

  def "verify retrieval of templated pipeline config using v1 approach results in null"() {
    given:

    def templatedPipeline = new Pipeline(
      id: "id-of-my-templated-pipeline",
      name: "name-of-my-templated-pipeline",
      application: "application",
      type: TYPE_TEMPLATED,
      schema: "v2",
      template: [
        reference: SPINNAKER_PREFIX + "myPipelineTemplateId"
      ]
    )
    def oneOtherTemplatedPipeline = new Pipeline(
      id: "id-of-one-other-templated-pipeline",
      name: "name-of-one-other-templated-pipeline",
      application: "application",
      type: TYPE_TEMPLATED,
      schema: "v2",
      template: [
        reference: SPINNAKER_PREFIX + "oneOtherPipelineTemplateId"
      ]
    )

    pipelineDAO.all() >> [templatedPipeline, oneOtherTemplatedPipeline]

    when:
    TemplateConfiguration config = controller.objectMapper.convertValue(
      templatedPipeline.getConfig(), TemplateConfiguration.class);

    then:
    config == null
  }

  def "getDependentConfigs returns empty list when there are no templated pipelines"() {
    given:
    pipelineDAO.all() >> []

    when:
    List<String> dependentConfigIds = controller.getDependentConfigs("myPipelineTemplateId")

    then:
    dependentConfigIds.isEmpty()
  }

  def "getDependentConfigs returns empty list when templated pipelines have no dependencies"() {
    given:
    Pipeline normalPipeline = new Pipeline()
    Pipeline templatedPipeline = new Pipeline(id: "id-of-my-templated-pipeline", type: TYPE_TEMPLATED)
    pipelineDAO.all() >> [normalPipeline, templatedPipeline]

    when:
    List<String> dependentConfigIds = controller.getDependentConfigs("a-different-pipeline-template-id")

    then:
    dependentConfigIds.isEmpty()
  }

  def "getDependentConfigs returns list of dependent pipeline IDs"() {
    given:

    def normalPipeline = new Pipeline(
      id: "normalPipeline",
      name: "normalPipeline",
      application: "application",
    )
    def templatedPipeline = new Pipeline(
      id: "id-of-my-templated-pipeline",
      name: "name-of-my-templated-pipeline",
      application: "application",
      type: TYPE_TEMPLATED,
      schema: "v2",
      template: [
        reference: SPINNAKER_PREFIX + "myPipelineTemplateId"
      ]
    )
    def oneOtherTemplatedPipeline = new Pipeline(
      id: "id-of-one-other-templated-pipeline",
      name: "name-of-one-other-templated-pipeline",
      application: "application",
      type: TYPE_TEMPLATED,
      schema: "v2",
      template: [
        reference: SPINNAKER_PREFIX + "oneOtherPipelineTemplateId"
      ]
    )

    pipelineDAO.all() >> [normalPipeline, templatedPipeline, oneOtherTemplatedPipeline]

    when:
    List<String> dependentConfigIds = controller.getDependentConfigs("myPipelineTemplateId")

    then:
    dependentConfigIds == ["id-of-my-templated-pipeline"]
  }

  @Unroll
  def 'should fail with InvalidEntityException if Id(#id) is not provided or empty'() {
    when:
    controller.save('latest', new PipelineTemplate(id: id))

    then:
    thrown(InvalidEntityException)

    where:
    id << [null, "", "    "]
  }
}
