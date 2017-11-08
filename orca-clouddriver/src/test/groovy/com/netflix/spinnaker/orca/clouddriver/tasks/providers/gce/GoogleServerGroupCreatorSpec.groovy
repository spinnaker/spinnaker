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

import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification

class GoogleServerGroupCreatorSpec extends Specification {

  def "should get operations"() {
    given:
      def ctx = [
          account          : "abc",
          region           : "north-pole",
          zone             : "north-pole-1",
          deploymentDetails: [[imageId: "testImageId", region: "north-pole"]],
      ]
    def stage = new Stage(Execution.newPipeline("orca"), "whatever", ctx)

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
                  deploymentDetails: [[imageId: "testImageId", region: "north-pole"]],
              ],
          ]
      ]

    when: "fallback to non-region matching image"
      ctx.region = "south-pole"
      ctx.zone = "south-pole-1"
    stage = new Stage(Execution.newPipeline("orca"), "whatever", ctx)
      ops = new GoogleServerGroupCreator().getOperations(stage)

    then:
      ops == [
          [
              "createServerGroup": [
                  account          : "abc",
                  credentials      : "abc",
                  image            : "testImageId",
                  region           : "south-pole",
                  zone             : "south-pole-1",
                  deploymentDetails: [[imageId: "testImageId", region: "north-pole"]],
              ],
          ]
      ]

    when: "throw error if no image found"
      ctx.deploymentDetails = []
    stage = new Stage(Execution.newPipeline("orca"), "whatever", ctx)
      new GoogleServerGroupCreator().getOperations(stage)

    then:
      IllegalStateException ise = thrown()
      ise.message == "No image could be found in south-pole."
  }
}
