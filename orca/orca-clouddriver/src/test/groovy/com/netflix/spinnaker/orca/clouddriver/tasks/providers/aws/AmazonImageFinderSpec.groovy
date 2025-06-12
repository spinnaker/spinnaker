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

import retrofit2.mock.Calls

import java.util.stream.Collectors
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import spock.lang.Specification
import spock.lang.Subject

class AmazonImageFinderSpec extends Specification {
  def objectMapper = new ObjectMapper()
  def oortService = Mock(OortService)

  @Subject
  def amazonImageFinder = new AmazonImageFinder(objectMapper: objectMapper, oortService: oortService)

  def "should prepend tag keys with 'tag:'"() {
    expect:
    amazonImageFinder.prefixTags([engine: "spinnaker"]) == ["tag:engine": "spinnaker"]
  }

  def "uses appversion tag to filter image results on package name"() {
    given:
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "", [
      regions: ["us-west-1", "us-west-2"]
    ])
    def tags = [
      appversion: "mypackage-2.79.0-h247.d14bad0/mypackage/247",
      build_host: "http://build.host"
    ]
    def wrongTags = [
      appversion: "mypackagecore-2.79.0-h247.d14bad0/mypackagecore/247",
      build_host: "http://build.host"
    ]
    def noTags = [:]

    when:
    def imageDetails = amazonImageFinder.byTags(stage, "mypackage", ["engine": "spinnaker"], [])

    then:
    1 * oortService.findImage("aws", "mypackage", null, null, ["tag:engine": "spinnaker"]) >> Calls.response(
      [
        [
          imageName    : "image-0",
          attributes   : [creationDate: bCD("2015")],
          tagsByImageId: ["ami-0": tags, "ami-1": tags],
          amis         : [
            "us-west-1": ["ami-0"],
            "us-west-2": ["ami-1"]
          ]
        ],
        [
          imageName    : "image-2",
          attributes   : [creationDate: bCD("2016")],
          tagsByImageId: ["ami-2": noTags],
          amis         : [
            "us-west-1": ["ami-2"]
          ]
        ],
        [
          imageName    : "image-3",
          attributes   : [creationDate: bCD("2016")],
          tagsByImageId: ["ami-3": wrongTags],
          amis         : [
            "us-west-2": ["ami-3"]
          ]
        ],
        [
          imageName    : "image-4",
          attributes   : [creationDate: bCD("2017")],
          tagsByImageId: ["ami-4": tags],
          amis         : [
            "us-west-2": ["ami-4"]
          ]
        ],
        [
          imageName    : "image-5",
          attributes   : [creationDate: bCD("2017")],
          tagsByImageId: ["ami-5": tags],
          amis         : [
            "us-west-1": ["ami-5"]
          ]
        ],
        [
          imageName    : "image-6",
          attributes   : [creationDate: bCD("2018")],
          tagsByImageId: ["ami-6": wrongTags],
          amis         : [
            "us-west-1": ["ami-6"]
          ]
        ]
      ])
    0 * _

    imageDetails.size() == 2
    imageDetails.every {
      (it.jenkins as Map) == [
        "number": "247",
        "host"  : "http://build.host",
        "name"  : "mypackage"
      ]
    }
    imageDetails.find { it.region == "us-west-1" }.imageId == "ami-5"
    imageDetails.find { it.region == "us-west-1" }.imageName == "image-5"
    imageDetails.find { it.region == "us-west-2" }.imageId == "ami-4"
    imageDetails.find { it.region == "us-west-2" }.imageName == "image-4"

  }

  def "should match most recently created image per region"() {
    given:
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "", [
      regions: ["us-west-1", "us-west-2"]
    ])
    def tags = [
      appversion: "mypackage-2.79.0-h247.d14bad0/mypackage/247",
      build_host: "http://build.host"
    ]

    when:
    def imageDetails = amazonImageFinder.byTags(stage, "mypackage", ["engine": "spinnaker"], [])

    then:
    1 * oortService.findImage("aws", "mypackage", null, null, ["tag:engine": "spinnaker"]) >> Calls.response(
      [
        [
          imageName    : "image-0",
          attributes   : [creationDate: bCD("2015")],
          tagsByImageId: ["ami-0": tags, "ami-1": tags],
          amis         : [
            "us-west-1": ["ami-0"],
            "us-west-2": ["ami-1"]
          ]
        ],
        [
          imageName    : "image-2",
          attributes   : [creationDate: bCD("2016")],
          tagsByImageId: ["ami-2": tags],
          amis         : [
            "us-west-1": ["ami-2"]
          ]
        ],
        [
          imageName    : "image-3",
          attributes   : [creationDate: bCD("2016")],
          tagsByImageId: ["ami-3": tags],
          amis         : [
            "us-west-2": ["ami-3"]
          ]
        ]
      ])
    0 * _

    imageDetails.size() == 2
    imageDetails.every {
      (it.jenkins as Map) == [
        "number": "247",
        "host"  : "http://build.host",
        "name"  : "mypackage"
      ]
    }
    imageDetails.find { it.region == "us-west-1" }.imageId == "ami-2"
    imageDetails.find { it.region == "us-west-1" }.imageName == "image-2"
    imageDetails.find { it.region == "us-west-2" }.imageId == "ami-3"
    imageDetails.find { it.region == "us-west-2" }.imageName == "image-3"
  }

  def "should skip images with incomplete information"() {
    given:
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "", [
      regions: ["us-west-2"]
    ])
    def tags = [
      appversion: "mypackage-2.79.0-h247.d14bad0/mypackage/247",
      build_host: "http://build.host"
    ]

    when:
    def imageDetails = amazonImageFinder.byTags(stage, "mypackage", ["engine": "spinnaker"], [])

    then:
    1 * oortService.findImage("aws", "mypackage", null, null, ["tag:engine": "spinnaker"]) >> Calls.response(
      [
        [
          imageName    : "image-0",
          attributes   : [creationDate: bCD("2015")],
          tagsByImageId: [:],
          amis         : [
            "us-west-2": ["ami-0"],
            "us-west-2": ["ami-1"]
          ]
        ],
        [
          imageName    : "image-2",
          attributes   : [creationDate: bCD("2016")],
          tagsByImageId: ["ami-2": tags],
          amis         : [
            "us-west-2": ["ami-2"]
          ]
        ],
        [
          imageName    : "image-3",
          attributes   : [creationDate: bCD("2017")],
          tagsByImageId: [:],
          amis         : [
            "us-west-2": ["ami-3"]
          ]
        ]
      ])
    0 * _

    imageDetails.size() == 1
    imageDetails.every {
      (it.jenkins as Map) == [
        "number": "247",
        "host"  : "http://build.host",
        "name"  : "mypackage"
      ]
    }
    imageDetails.find { it.region == "us-west-2" }.imageId == "ami-2"
    imageDetails.find { it.region == "us-west-2" }.imageName == "image-2"
  }

  def "should sort images by 'creationDate'"() {
    given:
    def images = creationDates.collect {
      new AmazonImageFinder.AmazonImage(attributes: [
        creationDate: it
      ])
    }

    expect:
    images.stream().sorted().collect(Collectors.toList())*.attributes*.creationDate == expectedOrder

    where:
    creationDates                                              || expectedOrder
    [bCD("2016"), bCD("2014"), bCD("2012"), bCD("2015")]       || [bCD("2016"), bCD("2015"), bCD("2014"), bCD("2012")]
    [bCD("2016"), bCD("2014"), null, bCD("2012"), bCD("2015")] || [bCD("2016"), bCD("2015"), bCD("2014"), bCD("2012"), null]
  }

  def 'passes imageOwnerAccount to oortService.findImage'() {
    given: 'an imageOwnerAccount property in the stage'
    String myAccount = 'my-account'
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "", [
      imageOwnerAccount: myAccount,
      regions: ["us-west-1"]
    ])

    when:
    amazonImageFinder.byTags(stage, 'mypackage', [:], [])

    then:
    1 * oortService.findImage('aws', 'mypackage', myAccount, null, _) >> Calls.response([])
    0 * _
  }

  String bCD(String year) {
    return "${year}-07-28T20:07:21.000Z"
  }
}
