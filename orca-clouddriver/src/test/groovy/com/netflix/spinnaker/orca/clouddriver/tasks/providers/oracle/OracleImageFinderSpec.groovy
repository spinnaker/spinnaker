/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */


package com.netflix.spinnaker.orca.clouddriver.tasks.providers.oracle

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.tasks.image.ImageFinder
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Subject

class OracleImageFinderSpec extends Specification {
  def objectMapper = new ObjectMapper()
  def oortService = Mock(OortService)

  @Subject
  def oracleImageFinder = new OracleImageFinder(oortService, objectMapper)

  def "should prepend tag keys with 'tag:'"() {
    expect:
    oracleImageFinder.prefixTags([engine: "spinnaker"]) == ["tag:engine": "spinnaker"]
  }

  def "should find first matching image for single region"() {
    given:
    def stage = new Stage(Execution.newPipeline("orca"), "", [
      regions: ["us-ashburn-1"]
    ])
    def tags = [
      appversion: "mypackage-2.79.0-h247.d14bad0/mypackage/247",
      build_host: "http://build.host"
    ]

    when:
    Collection<ImageFinder.ImageDetails> imageDetails = oracleImageFinder.byTags(
      stage, "imageName*", ["version": "latest"])

    then:
    1 * oortService.findImage("oracle", "imageName*", null, null, ["tag:version": "latest"]) >> {
      imagesFromMockClouddriver()
    }
    0 * _

    imageDetails.size() == 1 //only one should be found
    imageDetails.first().region == "us-ashburn-1" //for the right region
    imageDetails.first().imageId == "ocid.image1.ashburn.withversionlatest" //should be the first matching image in list
    imageDetails.first().imageName == "imageName1"
  }

  def "should match first image per region for multiple regions"() {
    given:
    def stage = new Stage(Execution.newPipeline("orca"), "", [
      regions: ["us-ashburn-1", "us-phoenix-1"]
    ])
    def tags = [
      version: "latest"
    ]

    when:
    def imageDetails = oracleImageFinder.byTags(stage, "image*", ["version": "latest"])

    then:
    1 * oortService.findImage("oracle", "image*", null, null, ["tag:version": "latest"]) >> {
      imagesFromMockClouddriver()
    }
    0 * _

    imageDetails.size() == 2
    imageDetails.find { it.region == "us-ashburn-1" }.imageId == "ocid.image1.ashburn.withversionlatest"
    imageDetails.find { it.region == "us-ashburn-1" }.imageName == "imageName1"
    imageDetails.find { it.region == "us-phoenix-1" }.imageId == "ocid.image1.phoenix.withversionlatest"
    imageDetails.find { it.region == "us-phoenix-1" }.imageName == "imageName1"
  }

  def imagesFromMockClouddriver() {
    return [
      [
        "id": "ocid.image1.phoenix.withversionlatest",
        "name": "imageName1",
        "region": "us-phoenix-1",
        "account": "myacct",
        "cloudProvider": "oracle",
        "compatibleShapes": [
          "VM.Standard1.1",
          "VM.Standard1.2",
          "VM.Standard1.4"
        ],
        "freeformTags": [
          "version": "latest"
        ],
        "timeCreated": "1534782746"
      ],
      [
        "id": "ocid.image0.ashburn.withversionlatest.oldTimeCreated",
        "name": "imageName0",
        "region": "us-ashburn-1",
        "account": "myacct",
        "cloudProvider": "oracle",
        "compatibleShapes": [
          "VM.Standard1.1",
          "VM.Standard1.2",
          "VM.Standard1.4"
        ],
        "freeformTags": [
          "version": "latest"
        ],
        "timeCreated": "1534437146"
      ],
      [
        "id": "ocid.image1.ashburn.withversionlatest",
        "name": "imageName1",
        "region": "us-ashburn-1",
        "account": "myacct",
        "cloudProvider": "oracle",
        "compatibleShapes": [
          "VM.Standard1.1",
          "VM.Standard1.2",
          "VM.Standard1.4"
        ],
        "freeformTags": [
          "version": "latest"
        ],
        "timeCreated": "1534782746"
      ],
      [
        "id": "ocid.image1.ashburn.withversionlatest",
        "name": "imageName2",
        "region": "us-ashburn-1",
        "account": "myacct",
        "cloudProvider": "oracle",
        "compatibleShapes": [
          "VM.Standard1.1",
          "VM.Standard1.2",
          "VM.Standard1.4"
        ],
        "freeformTags": [
          "version": "latest"
        ],
        "timeCreated": "1534782746"
      ]
    ]
  }
}
