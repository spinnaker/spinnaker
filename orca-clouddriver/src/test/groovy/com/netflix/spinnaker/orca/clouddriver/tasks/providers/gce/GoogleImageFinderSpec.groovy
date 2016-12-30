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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.clouddriver.OortService
import spock.lang.Specification
import spock.lang.Subject

import java.util.stream.Collectors

class GoogleImageFinderSpec extends Specification {
  def objectMapper = new ObjectMapper()
  def oortService = Mock(OortService)

  @Subject
  def googleImageFinder = new GoogleImageFinder(objectMapper: objectMapper, oortService: oortService)

  def "should prepend tag keys with 'tag:'"() {
    expect:
    googleImageFinder.prefixTags([engine: "spinnaker"]) == ["tag:engine": "spinnaker"]
  }

  def "should match most recently created image"() {
    given:
    def tags = [
      appversion: "mypackage-2.79.0-h247.d14bad0/mypackage/247",
      build_host: "http://build.host"
    ]

    when:
    def imageDetails = googleImageFinder.byTags(null, "mypackage", ["engine": "spinnaker"])

    then:
    1 * oortService.findImage("gce", "mypackage", null, null, ["tag:engine": "spinnaker"]) >> {
      [
        [
          imageName : "image-0",
          attributes: [creationDate: bCD("2014")],
          tags      : tags
        ],
        [
          imageName : "image-2",
          attributes: [creationDate: bCD("2016")],
          tags      : tags
        ],
        [
          imageName : "image-3",
          attributes: [creationDate: bCD("2015")],
          tags      : tags
        ]
      ]
    }
    0 * _

    imageDetails.size() == 1
    imageDetails[0].imageId == "image-2"
    imageDetails[0].imageName == "image-2"
    imageDetails[0].jenkins as Map == [
      "number": "247",
      "host"  : "http://build.host",
      "name"  : "mypackage"
    ]
  }

  def "should sort images by 'creationDate'"() {
    given:
    def images = creationDates.collect {
      new GoogleImageFinder.GoogleImage(attributes: [
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

  String bCD(String year) {
    return "${year}-07-28T20:07:21.881-08:00"
  }
}
