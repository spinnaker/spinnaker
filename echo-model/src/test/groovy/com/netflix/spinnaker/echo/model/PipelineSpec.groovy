/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.echo.model

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper
import spock.lang.Shared
import spock.lang.Specification

class PipelineSpec extends Specification {

  @Shared
  ObjectMapper objectMapper = EchoObjectMapper.getInstance()

  void setupSpec() {
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
  }

  void 'pipeline config deserialization should work fine'() {
    setup:
    String pipelineJson = ''' {
            "name": "Testing Triggering",
            "stages": [
                {
                    "type": "wait",
                    "name": "First",
                    "waitTime": 2
                },
                {
                    "type": "wait",
                    "name": "Second",
                    "waitTime": 5,
                    "restrictExecutionDuringTimeWindow": false,
                    "restrictedExecutionWindow": {
                        "whitelist": []
                    }
                }
            ],
            "triggers": [
                {
                    "enabled": false,
                    "type": "cron",
                    "id": "ec2728d9-9dd2-43da-ab91-1e318ef592af",
                    "cronExpression": "* 0/30 * * * ? *"
                }
            ],
            "application": "api",
            "index": 1,
            "id": "68a14710-1ade-11e5-89a8-65c9c7540d0f"
        }
        '''
    when:
    Pipeline pipeline = objectMapper.readValue(pipelineJson, Pipeline)

    then:
    noExceptionThrown()
    pipeline != null
    pipeline.id == '68a14710-1ade-11e5-89a8-65c9c7540d0f'
    pipeline.name == 'Testing Triggering'
    pipeline.application == 'api'
    pipeline.triggers?.size() == 1
    pipeline.triggers[0] != null
    pipeline.triggers[0].id == 'ec2728d9-9dd2-43da-ab91-1e318ef592af'
    pipeline.triggers[0].type == 'cron'
    pipeline.triggers[0].cronExpression == '* 0/30 * * * ? *'
    !pipeline.triggers[0].enabled
  }

  void 'pipeline configs deserialization should work fine'() {
    setup:
    String pipelineJson = ''' [
            {
                "name": "Testing Triggering",
                "stages": [
                    {
                        "type": "wait",
                        "name": "Second",
                        "waitTime": 5,
                        "restrictExecutionDuringTimeWindow": false,
                        "restrictedExecutionWindow": {
                            "whitelist": []
                        }
                    }
                ],
                "triggers": [
                    {
                        "enabled": false,
                        "type": "cron",
                        "id": "ec2728d9-9dd2-43da-ab91-1e318ef592af",
                        "cronExpression": "* 0/30 * * * ? *"
                    }
                ],
                "application": "api",
                "index": 1,
                "id": "68a14710-1ade-11e5-89a8-65c9c7540d0f"
            },
            {
                "name": "Testing Execution Windows",
                "stages": [
                    {
                        "type": "wait",
                        "name": "First",
                        "waitTime": 2
                    }
                ],
                "triggers": [
                    {
                        "enabled": true,
                        "type": "jenkins",
                        "master": "edge",
                        "job": "EDGE-Master-Family-Build"
                    }
                ],
                "application": "api",
                "index": 2,
                "id": "e06e9950-113d-11e5-ae15-5107b26a5b34"
            }
        ]
        '''
    when:
    List<Pipeline> pipelines = objectMapper.readValue(pipelineJson, new TypeReference<List<Pipeline>>() {})

    then:
    noExceptionThrown()
    pipelines?.size() == 2
    pipelines[0] != null
    pipelines[0].id == '68a14710-1ade-11e5-89a8-65c9c7540d0f'
    pipelines[0].triggers?.size() == 1
    pipelines[0].triggers[0] != null
    pipelines[1] != null
    pipelines[1].id == 'e06e9950-113d-11e5-ae15-5107b26a5b34'
    pipelines[1].triggers?.size() == 1
    pipelines[1].triggers[0] != null
  }

  void 'pipeline config deserialization for pipelines with no triggers should work fine'() {
    setup:
    String pipelineJson = ''' {
            "name": "Testing Triggering",
            "stages": [
                {
                    "type": "wait",
                    "name": "First",
                    "waitTime": 2
                }
            ],
            "triggers": [],
            "application": "api",
            "index": 1,
            "id": "68a14710-1ade-11e5-89a8-65c9c7540d0f"
        }
        '''
    when:
    Pipeline pipeline = objectMapper.readValue(pipelineJson, Pipeline)

    then:
    noExceptionThrown()
    pipeline != null
    pipeline.id == '68a14710-1ade-11e5-89a8-65c9c7540d0f'
    pipeline.name == 'Testing Triggering'
    pipeline.application == 'api'
    pipeline.triggers?.size() == 0
  }

  void 'pipeline config deserialization for pipelines with jenkins trigger should work fine'() {
    String pipelineJson = ''' {
            "name": "Testing Execution Windows",
            "stages": [
                {
                    "type": "wait",
                    "name": "First",
                    "waitTime": 2
                }
            ],
            "triggers": [
                {
                    "enabled": true,
                    "type": "jenkins",
                    "master": "edge",
                    "job": "EDGE-Master-Family-Build"
                }
            ],
            "application": "api",
            "index": 2,
            "id": "e06e9950-113d-11e5-ae15-5107b26a5b34"
        }
        '''

    when:
    Pipeline pipeline = objectMapper.readValue(pipelineJson, Pipeline)

    then:
    noExceptionThrown()
    pipeline != null
    pipeline.id == 'e06e9950-113d-11e5-ae15-5107b26a5b34'
    pipeline.triggers?.size() == 1
    pipeline.triggers[0] != null
    pipeline.triggers[0].id == null
    pipeline.triggers[0].type == 'jenkins'
    pipeline.triggers[0].cronExpression == null
    pipeline.triggers[0].enabled == true
  }

  void 'pipeline config deserialization with multiple triggers should work fine'() {
    setup:
    String pipelineJson = ''' {
            "name": "Testing Triggering",
            "stages": [
                {
                    "type": "wait",
                    "name": "First",
                    "waitTime": 2
                }
            ],
            "triggers": [
                {
                    "enabled": false,
                    "type": "cron",
                    "id": "ec2728d9-9dd2-43da-ab91-1e318ef592af",
                    "cronExpression": "* 0/30 * * * ? *"
                },
                {
                    "enabled": true,
                    "type": "jenkins",
                    "master": "edge",
                    "job": "EDGE-Master-Family-Build"
                }
            ],
            "application": "api",
            "id": "68a14710-1ade-11e5-89a8-65c9c7540d0f"
        }
        '''
    when:
    Pipeline pipeline = objectMapper.readValue(pipelineJson, Pipeline)

    then:
    noExceptionThrown()
    pipeline != null
    pipeline.id == '68a14710-1ade-11e5-89a8-65c9c7540d0f'
    pipeline.name == 'Testing Triggering'
    pipeline.application == 'api'
    pipeline.triggers?.size() == 2
    pipeline.triggers[0] != null
    pipeline.triggers[0].id == 'ec2728d9-9dd2-43da-ab91-1e318ef592af'
    pipeline.triggers[0].type == 'cron'
    pipeline.triggers[0].cronExpression == '* 0/30 * * * ? *'
    !pipeline.triggers[0].enabled
    pipeline.triggers[1] != null
    pipeline.triggers[1].id == null
    pipeline.triggers[1].type == 'jenkins'
    pipeline.triggers[1].cronExpression == null
    pipeline.triggers[1].enabled

  }

  void 'pipeline config deserialization should work fine for templated pipelines'() {
    setup:
    String pipelineJson = ''' {
            "name": "Testing Templates",
            "stages": [],
            "config": {
                "pipeline": {
                    "application": "testapp",
                    "name": "testing",
                    "pipelineConfigId": "4a522db6-b127-47b2-9c42-9b01b2625ce1",
                    "template": {
                        "source": "http://my.source"
                    },
                    "variables": {}
                },
                "schema": "1"
            },
            "triggers": [],
            "application": "testapp",
            "index": 1,
            "id": "68a14710-1ade-11e5-89a8-65c9c7540d0f"
        }
        '''
    when:
    Pipeline pipeline = objectMapper.readValue(pipelineJson, Pipeline)

    then:
    noExceptionThrown()
    pipeline != null
    pipeline.id == '68a14710-1ade-11e5-89a8-65c9c7540d0f'
    pipeline.name == 'Testing Templates'
    pipeline.application == 'testapp'
    pipeline.config.pipeline.pipelineConfigId == '4a522db6-b127-47b2-9c42-9b01b2625ce1'

    when:
    String pipelineAsString = objectMapper.writeValueAsString(pipeline)
    Pipeline pipelineRoundTrip = objectMapper.readValue(pipelineAsString, Pipeline)

    then:
    noExceptionThrown()
    pipeline == pipelineRoundTrip
  }
}
