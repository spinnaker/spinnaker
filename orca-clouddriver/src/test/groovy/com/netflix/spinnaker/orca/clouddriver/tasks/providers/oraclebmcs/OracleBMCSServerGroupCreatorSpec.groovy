/*
 * Copyright (c) 2017 Oracle America, Inc.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.orca.clouddriver.tasks.providers.oraclebmcs

import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification

class OracleBMCSServerGroupCreatorSpec extends Specification {

  def "should get operations"() {
    given:
    def ctx = [
      account          : "abc",
      region           : "north-pole",
      zone             : "north-pole-1",
      deploymentDetails: [[imageId: "testImageId", region: "north-pole"]],
    ]
    def stage = new Stage(new Pipeline(), "whatever", ctx)

    when:
    def ops = new OracleBMCSServerGroupCreator().getOperations(stage)

    then:
    ops == [
      [
        "createServerGroup": [
          account          : "abc",
          credentials      : "abc",
          imageId          : "testImageId",
          region           : "north-pole",
          zone             : "north-pole-1",
          deploymentDetails: [[imageId: "testImageId", region: "north-pole"]],
        ],
      ]
    ]

    when: "fallback to non-region matching image"
    ctx.region = "south-pole"
    ctx.zone = "south-pole-1"
    stage = new Stage(new Pipeline(), "whatever", ctx)
    ops = new OracleBMCSServerGroupCreator().getOperations(stage)

    then:
    ops == [
      [
        "createServerGroup": [
          account          : "abc",
          credentials      : "abc",
          imageId          : "testImageId",
          region           : "south-pole",
          zone             : "south-pole-1",
          deploymentDetails: [[imageId: "testImageId", region: "north-pole"]],
        ],
      ]
    ]

    when: "throw error if no image found"
    ctx.deploymentDetails = []
    stage = new Stage(new Pipeline(), "whatever", ctx)
    new OracleBMCSServerGroupCreator().getOperations(stage)

    then:
    IllegalStateException ise = thrown()
    ise.message == "No imageId could be found in south-pole."
  }
}
