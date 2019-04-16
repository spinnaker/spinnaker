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
import com.netflix.spinnaker.orca.clouddriver.tasks.image.ImageTagger
import com.netflix.spinnaker.orca.clouddriver.tasks.image.ImageTaggerSpec
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.test.model.ExecutionBuilder
import spock.lang.Unroll

class AmazonImageTaggerSpec extends ImageTaggerSpec<AmazonImageTagger> {

  def oortService = Mock(OortService)

  @Override
  protected AmazonImageTagger subject() {
    def imageTagger = new AmazonImageTagger(oortService, new ObjectMapper())
    imageTagger.defaultBakeAccount = "test"
    return imageTagger
  }

  @Unroll
  def "should throw exception if image does not exist"() {
    given:
    def pipeline = Execution.newPipeline("orca")
    def stage1 = new Stage(pipeline, "", [
      imageId      : imageId,
      cloudProvider: "aws"
    ])
    def stage2 = new Stage(pipeline, "", [
      imageNames   : imageName ? [imageName] : null,
      cloudProvider: "aws"
    ])

    stage1.refId = stage1.id
    stage2.requisiteStageRefIds = [stage1.refId]

    pipeline.stages << stage1 << stage2

    and:
    if (foundById) {
      1 * oortService.findImage("aws", "ami-id", null, null, null) >> {
        [["imageName": "ami-name"]]
      }
      1 * oortService.findImage("aws", "ami-name", null, null, null) >> { [] }
    } else if (imageId != null) {
      1 * oortService.findImage("aws", imageId, null, null, null) >> { [] }
    } else {
      1 * oortService.findImage("aws", imageName, null, null, null) >> { [] }
    }

    when:
    imageTagger.getOperationContext(stage2)

    then:
    ImageTagger.ImageNotFound e = thrown(ImageTagger.ImageNotFound)
    e.shouldRetry == shouldRetry

    where:
    imageId  | imageName  || foundById || shouldRetry
    "ami-id" | null       || false     || true
    "ami-id" | null       || true      || true
    null     | "ami-name" || false     || false  // do not retry if an explicitly provided image does not exist (user error)
  }

  def "retries when namedImage data is missing an upstream imageId"() {
    given:
    def name = "spinapp-1.0.0-ebs"
    def pipeline = ExecutionBuilder.pipeline {}
    def stage1 = new Stage(
      pipeline,
      "bake",
      [
        cloudProvider: "aws",
        imageId      : "ami-1",
        imageName    : name,
        region       : "us-east-1"
      ]
    )

    def stage2 = new Stage(
      pipeline,
      "bake",
      [
        cloudProvider: "aws",
        imageId      : "ami-2",
        imageName    : name,
        region       : "us-west-1"
      ]
    )

    def stage3 = new Stage(pipeline, "upsertImageTags", [imageName: name, cloudProvider: "aws"])

    stage1.refId = stage1.id
    stage2.refId = stage2.id
    stage3.requisiteStageRefIds = [stage1.refId, stage2.refId]

    pipeline.stages << stage1 << stage2 << stage3

    when:
    1 * oortService.findImage("aws", "ami-1", _, _, _) >> {
      [[imageName: name]]
    }

    1 * oortService.findImage("aws", "ami-2", _, _, _) >> {
      [[imageName: name]]
    }

    1 * oortService.findImage("aws", name, _, _, _) >> {
      [[
         imageName: name,
         amis     : ["us-east-1": ["ami-1"]]
       ]]
    }

    imageTagger.getOperationContext(stage3)

    then:
    ImageTagger.ImageNotFound e = thrown(ImageTagger.ImageNotFound)
    e.shouldRetry == true

    when:
    1 * oortService.findImage("aws", "ami-1", _, _, _) >> {
      [[imageName: name]]
    }

    1 * oortService.findImage("aws", "ami-2", _, _, _) >> {
      [[imageName: name]]
    }

    1 * oortService.findImage("aws", name, _, _, _) >> {
      [[
         imageName: name,
         amis     : [
           "us-east-1": ["ami-1"],
           "us-west-1": ["ami-2"]
         ],
         accounts : ["compute"]
       ]]
    }

    imageTagger.getOperationContext(stage3)

    then:
    noExceptionThrown()
  }

  def "should build upsertMachineImageTags and allowLaunchDescription operations"() {
    given:
    def stage = new Stage(Execution.newOrchestration("orca"), "", [
      imageNames: ["my-ami"],
      tags      : [
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
    operationContext.extraOutput.originalTags == ["my-ami": ["my-ami-00001": [tag1: "originalValue1"]]]
  }

  def "should apply regions based on AMI data from Clouddriver"() {
    given:
    def stage = new Stage(Execution.newOrchestration("orca"), "", [
      imageNames: ["my-ami-1", "my-ami-2"],
      tags      : [
        "tag1": "value1"
      ]
    ])

    when:
    def operationContext = imageTagger.getOperationContext(stage)

    then:
    1 * oortService.findImage("aws", "my-ami-1", null, null, null) >> {
      [
        [imageName: "my-ami-1", accounts: ["test"], amis: ["us-east-1": ["my-ami-00002"]]]
      ]
    }
    1 * oortService.findImage("aws", "my-ami-2", null, null, null) >> {
      [
        [imageName: "my-ami-2", accounts: ["test"], amis: ["us-west-1": ["my-ami-00001"]]]
      ]
    }

    operationContext.operations.size() == 2
    operationContext.operations[0]["upsertImageTags"] == [
      amiName    : "my-ami-1",
      tags       : [
        "tag1": "value1"
      ],
      regions    : ["us-east-1"] as Set<String>,
      credentials: imageTagger.defaultBakeAccount
    ]
    operationContext.operations[1]["upsertImageTags"] == [
      amiName    : "my-ami-2",
      tags       : [
        "tag1": "value1"
      ],
      regions    : ["us-west-1"] as Set<String>,
      credentials: imageTagger.defaultBakeAccount
    ]
    operationContext.extraOutput.regions == ["us-east-1", "us-west-1"]
  }
}
