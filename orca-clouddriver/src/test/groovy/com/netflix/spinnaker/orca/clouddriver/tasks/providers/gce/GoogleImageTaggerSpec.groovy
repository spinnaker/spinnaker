/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.gce

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.tasks.image.ImageTagger
import com.netflix.spinnaker.orca.clouddriver.tasks.image.ImageTaggerSpec
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Unroll

class GoogleImageTaggerSpec extends ImageTaggerSpec {

  def oortService = Mock(OortService)

  @Override
  protected ImageTagger subject() {
    new GoogleImageTagger(oortService, new ObjectMapper())
  }

  @Unroll
  def "should throw exception if image does not exist"() {
    given:
    def pipeline = Execution.newPipeline("orca")

    def stage1 = new Stage(pipeline, "", [
      imageId      : imageId,
      cloudProvider: "gce"
    ])
    def stage2 = new Stage(pipeline, "", [
      imageNames   : imageName ? [imageName] : null,
      cloudProvider: "gce"
    ])

    stage1.refId = stage1.id
    stage2.requisiteStageRefIds = [stage1.refId]

    pipeline.stages << stage1 << stage2

    and:
    oortService.findImage("gce", "my-gce-image", null, null, null) >> { [] }

    when:
    imageTagger.getOperationContext(stage2)

    then:
    ImageTagger.ImageNotFound e = thrown(ImageTagger.ImageNotFound)
    e.shouldRetry == shouldRetry

    where:
    imageId        | imageName       || shouldRetry
    "my-gce-image" | null            || true
    null           | "my-gce-image"  || false       // do not retry if an explicitly provided image does not exist (user error)
  }

  def "should build upsertImageTags operation"() {
    given:
    def stage = new Stage(Execution.newOrchestration("orca"), "", [
      account   : "my-google-account",
      imageNames: ["my-gce-image"],
      tags      : [
        "tag1"      : "value1",
        "appversion": "updated app version" // builtin tags should not be updatable
      ]
    ])

    when:
    def operationContext = imageTagger.getOperationContext(stage)

    then:
    1 * oortService.findImage("gce", "my-gce-image", null, null, null) >> {
      [
        [imageName: "my-gce-image-v2", account: "test"],
        [imageName: "my-gce-image", account: "test", tags: [tag1: "originalValue1"]]
      ]
    }

    operationContext.operations.size() == 1
    operationContext.operations[0]["upsertImageTags"] == [
      imageName  : "my-gce-image",
      tags       : [
        "tag1": "value1"
      ],
      credentials: "my-google-account"
    ]
    operationContext.extraOutput.targets.size() == 1
    operationContext.extraOutput.targets[0].imageName == "my-gce-image"
    operationContext.extraOutput.originalTags == ["my-gce-image": ["tag1": "originalValue1"]]
  }
}
