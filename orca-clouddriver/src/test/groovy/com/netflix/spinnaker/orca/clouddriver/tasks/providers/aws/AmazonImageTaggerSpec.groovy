/*
 * Copyright 2016 Netflix, Inc.
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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.OrchestrationStage
import spock.lang.Specification
import spock.lang.Subject

class AmazonImageTaggerSpec extends Specification {

  def oortService = Mock(OortService)

  @Subject
  def imageTagger = new AmazonImageTagger(
    objectMapper: new ObjectMapper(),
    oortService: oortService,
    defaultBakeAccount: "test"
  )

  def "should throw exception if image does not exist"() {
    given:
    def stage = new OrchestrationStage(new Orchestration(), "", [
      imageName: "my-ami"
    ])

    when:
    imageTagger.getOperationContext(stage)

    then:
    thrown(IllegalArgumentException)

    1 * oortService.findImage("aws", "my-ami", null, null, null) >> { [] }
  }

  def "should build upsertMachineImageTags and allowLaunchDescription operations"() {
    given:
    def stage = new OrchestrationStage(new Orchestration(), "", [
      imageName: "my-ami",
      tags   : [
        "tag1"      : "value1",
        "appversion": "updated app version" // builtin tags should not be updatable
      ]
    ])

    when:
    def operationContext = imageTagger.getOperationContext(stage)

    then:
    1 * oortService.findImage("aws", "my-ami", null, null, null) >> {
      [
        [imageName: "my-ami-v2", accounts: ["test"], amis: ["us-east-1": ["my-ami-00002"]]],
        [imageName: "my-ami", accounts: ["test", "prod"], amis: ["us-east-1": ["my-ami-00001"]], tagsByImageId: ["my-ami-00001": [tag1: "originalValue1"]]]
      ]
    }

    operationContext.operations.size() == 2
    operationContext.operations[0]["upsertImageTags"] == [
      amiName    : "my-ami",
      tags       : [
        "tag1": "value1"
      ],
      regions    : ["us-east-1"] as Set<String>,
      credentials: imageTagger.defaultBakeAccount
    ]
    operationContext.operations[1]["allowLaunchDescription"] == [
      amiName    : "my-ami",
      account    : "prod",
      region     : "us-east-1",
      credentials: imageTagger.defaultBakeAccount
    ]
    operationContext.extraOutput.regions == ["us-east-1"]
    operationContext.extraOutput.originalTags == ["my-ami-00001": [tag1: "originalValue1"]]
  }
}
