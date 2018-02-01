/*
 * Copyright 2016 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.cf

import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification

class CloudFoundryServerGroupCreatorSpec extends Specification {

  def "should get operations"() {
    given:
    def ctx = [
        account          : "abc",
        zone             : "north-pole-1",
        deploymentDetails: [[imageId: "testImageId", zone: "north-pole-1"]],
    ]
    def stage = new Stage(Execution.newPipeline("orca"), "whatever", ctx)

    when:
    def ops = new CloudFoundryServerGroupCreator().getOperations(stage)

    then:
    ops == [
        [
            "createServerGroup": [
                account          : "abc",
                credentials      : "abc",
                image            : "testImageId",
                zone             : "north-pole-1",
                trigger          : null,
                deploymentDetails: [[imageId: "testImageId", zone: "north-pole-1"]],
            ],
        ]
    ]

    when: "fallback to non-zone matching image"
    ctx.zone = "south-pole-1"
    stage = new Stage(Execution.newPipeline("orca"), "whatever", ctx)
    ops = new CloudFoundryServerGroupCreator().getOperations(stage)

    then:
    ops == [
        [
            "createServerGroup": [
                account          : "abc",
                credentials      : "abc",
                image            : "testImageId",
                zone             : "south-pole-1",
                trigger          : null,
                deploymentDetails: [[imageId: "testImageId", zone: "north-pole-1"]],
            ],
        ]
    ]

    when: "throw error if >1 image"
    ctx.deploymentDetails = [[imageId: "testImageId-1", zone: "east-pole-1"],
                             [imageId: "testImageId-2", zone: "west-pole-1"]]
    stage = new Stage(Execution.newPipeline("orca"), "whatever", ctx)
    ops = new CloudFoundryServerGroupCreator().getOperations(stage)

    then:
    IllegalStateException ise = thrown()
    ise.message.startsWith("Ambiguous choice of deployment images")

    when: "throw error if no image found"
    ctx.deploymentDetails = []
    stage = new Stage(Execution.newPipeline("orca"), "whatever", ctx)
    ops = new CloudFoundryServerGroupCreator().getOperations(stage)

    then:
    ise = thrown()
    ise.message == "Neither an image nor a repository/artifact could be found in south-pole-1."
  }

}
