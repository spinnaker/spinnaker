/*
 * Copyright 2016 Google, Inc.
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

import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import spock.lang.Specification

class GoogleServerGroupCreatorSpec extends Specification {

  def "should get operations"() {
    given:
      def ctx = [
          account          : "abc",
          region           : "north-pole",
          zone             : "north-pole-1",
          deploymentDetails: [[imageId: "testImageId", zone: "north-pole-1"]],
      ]
      def stage = new PipelineStage(new Pipeline(), "whatever", ctx)

    when:
      def ops = new GoogleServerGroupCreator().getOperations(stage)

    then:
      ops == [
          [
              "createServerGroup": [
                  account          : "abc",
                  credentials      : "abc",
                  image            : "testImageId",
                  region           : "north-pole",
                  zone             : "north-pole-1",
                  deploymentDetails: [[imageId: "testImageId", zone: "north-pole-1"]],
              ],
          ]
      ]

    when: "fallback to non-zone matching image"
      ctx.zone = "south-pole-1"
      stage = new PipelineStage(new Pipeline(), "whatever", ctx)
      ops = new GoogleServerGroupCreator().getOperations(stage)

    then:
      ops == [
          [
              "createServerGroup": [
                  account          : "abc",
                  credentials      : "abc",
                  image            : "testImageId",
                  region           : "north-pole",
                  zone             : "south-pole-1",
                  deploymentDetails: [[imageId: "testImageId", zone: "north-pole-1"]],
              ],
          ]
      ]

    when: "throw error if >1 image"
      ctx.deploymentDetails = [[imageId: "testImageId-1", zone: "east-pole-1"],
                               [imageId: "testImageId-2", zone: "west-pole-1"]]
      stage = new PipelineStage(new Pipeline(), "whatever", ctx)
      new GoogleServerGroupCreator().getOperations(stage)

    then:
      IllegalStateException ise = thrown()
      ise.message.startsWith("Ambiguous choice of deployment images")

    when: "throw error if no image found"
      ctx.deploymentDetails = []
      stage = new PipelineStage(new Pipeline(), "whatever", ctx)
      new GoogleServerGroupCreator().getOperations(stage)

    then:
      ise = thrown()
      ise.message == "No image could be found in north-pole."
  }
}
