/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.openstack

import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import spock.lang.Specification

class OpenstackServerGroupCreatorSpec extends Specification {

  def "should get operations"() {
    given:
    def ctx = [
      account          : "abc",
      region           : "north-pole",
      deploymentDetails: [[imageId: "testImageId"]]
    ]
    def stage = new PipelineStage(new Pipeline(), "whatever", ctx)

    when:
    def ops = new OpenstackServerGroupCreator().getOperations(stage)

    then:
    ops == [
      [
        "createServerGroup": [
          account              : "abc",
          region               : "north-pole",
          serverGroupParameters: [
            image: "testImageId",
          ],
          deploymentDetails    : [[imageId: "testImageId"]],
        ]
      ]
    ]

    when: "throw error if >1 image"
    ctx.deploymentDetails = [[imageId: "testImageId-1"],
                             [imageId: "testImageId-2"]]
    stage = new PipelineStage(new Pipeline(), "whatever", ctx)
    new OpenstackServerGroupCreator().getOperations(stage)

    then:
    IllegalStateException ise = thrown()
    ise.message.startsWith("Ambiguous choice of deployment images")

    when: "throw error if no image found"
    ctx.deploymentDetails = []
    stage = new PipelineStage(new Pipeline(), "whatever", ctx)
    new OpenstackServerGroupCreator().getOperations(stage)

    then:
    ise = thrown()
    ise.message == "No image could be found in north-pole."
  }

  def "should get image from provider context"() {
    given:
    String imageId = UUID.randomUUID().toString()
    def ctx = [
      account          : "abc",
      region           : "north-pole",
      deploymentDetails: [[cloudProviderType: "openstack",
                           imageId: imageId]]
    ]
    def stage = new PipelineStage(new Pipeline(), "whatever", ctx)

    when:
    def ops = new OpenstackServerGroupCreator().getOperations(stage)

    then:
    ops == [
      [
        "createServerGroup": [
          account              : "abc",
          region               : "north-pole",
          serverGroupParameters: [
            image: imageId,
          ],
          deploymentDetails    : [[cloudProviderType: "openstack",
                                   imageId: imageId]]
        ]
      ]
    ]
  }

  def "should get image from context"() {
    given:
    String imageId = UUID.randomUUID().toString()
    def ctx = [
      account          : "abc",
      region           : "north-pole",
      deploymentDetails: [[cloudProviderType: "foobar",
                           imageId: imageId]]
    ]
    def stage = new PipelineStage(new Pipeline(), "whatever", ctx)

    when:
    def ops = new OpenstackServerGroupCreator().getOperations(stage)

    then:
    ops == [
      [
        "createServerGroup": [
          account              : "abc",
          region               : "north-pole",
          serverGroupParameters: [
            image: imageId,
          ],
          deploymentDetails    : [[cloudProviderType: "foobar",
                                   imageId: imageId]]
        ]
      ]
    ]
  }

}
