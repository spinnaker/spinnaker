/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline

import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import spock.lang.Shared
import spock.lang.Specification

class PipelineConfigParsingSpec extends Specification {

  @Shared pipelineJson = """\
{
  "application": "front50",
  "name": "deploy to prestaging",
  "stages": [
    {
      "job": "SPINNAKER-package-front50",
      "master": "spinnaker-builds",
      "type": "jenkins"
    },
    {
      "baseLabel": "release",
      "baseOs": "ubuntu",
      "package": "front50",
      "region": "us-west-1",
      "type": "bake",
      "user": "orca"
    },
    {
      "account": "prod",
      "cluster": {
        "application": "front50",
        "availabilityZones": {
          "us-west-1": []
        },
        "capacity": {
          "desired": 1,
          "max": 1,
          "min": 1
        },
        "iamRole": "SpinnakerInstanceProfile",
        "instanceType": "m3.medium",
        "loadBalancers": [
          "front50-prestaging-frontend"
        ],
        "securityGroups": [
          "nf-infrastructure-vpc",
          "nf-datacenter-vpc"
        ],
        "stack": "prestaging",
        "strategy": "highlander",
        "subnetType": "internal"
      },
      "type": "deploy"
    }
  ],
  "version": "1.0"
}"""

  @Shared mapper = new OrcaObjectMapper()

  def "parses Pipeline object from JSON"() {
    when:
    def pipeline = PipelineStarter.parseConfig(jsonMap)

    then:
    with(pipeline) {
      application == "front50"
      stages.type == ["jenkins", "bake", "deploy"]
      stages.execution.every { it == pipeline }
      stages[2].context.cluster.application == "front50"
      stages[2].context.cluster.securityGroups == [
        "nf-infrastructure-vpc",
        "nf-datacenter-vpc"
      ]
    }

    where:
    jsonMap = mapper.readValue(pipelineJson, Map)
  }

}
