/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */

package com.netflix.spinnaker.clouddriver.oracle.controller

import com.netflix.spinnaker.clouddriver.oracle.model.OracleImage
import com.netflix.spinnaker.clouddriver.oracle.provider.view.OracleImageProvider
import spock.lang.Specification

import javax.servlet.http.HttpServletRequest

class OracleImageLookupControllerSpec extends Specification {

  def "test get image details by Id"() {
    setup:
    def imageProvider = Mock(OracleImageProvider)
    def imageLookupController = new OracleImageLookupController(imageProvider)
    def images = [
      new OracleImage(id: "ocid.image.123",
        name: "My Image",
        compatibleShapes: ["small"]),
      new OracleImage(id: "ocid.image.234",
        name: "My Other Image",
        compatibleShapes: ["small"])
    ]

    when:
    def results = imageLookupController.getById("DEFAULT", "AD1", "ocid.image.123")

    then:
    1 * imageProvider.getByAccountAndRegion("DEFAULT", "AD1") >> images
    results.size() == 1
    results.first().name == "My Image"
  }

  def "test find image by query"() {
    setup:
    def imageProvider = Mock(OracleImageProvider)
    def imageLookupController = new OracleImageLookupController(imageProvider)
    def httpServletRequest = mockHttpServletRequest()
    def images = [
      new OracleImage(id: "ocid.image.123",
        name: "foo 1",
        compatibleShapes: ["small"]),
      new OracleImage(id: "ocid.image.234",
        name: "foo 2",
        compatibleShapes: ["small"],
        freeformTags: ["tag1":"value1", "tag2":"value2"]),
      new OracleImage(id: "ocid.image.345",
        name: "bar 1",
        compatibleShapes: ["small"])
    ]

    when:
    def results = imageLookupController.find("DEFAULT", "foo", httpServletRequest)

    then:
    1 * imageProvider.getAll() >> images
    results.size() == 2
    results.contains(images[0])
    results.contains(images[1])
  }

  void "should extract tags from query parameters"() {
    given:
    def httpServletRequest = mockHttpServletRequest(["tag:tag1": "value1", "tag:Tag2": "value2"])

    expect:
    OracleImageLookupController.extractTagFilters(httpServletRequest) == ["tag1": "value1", "tag2": "value2"]
  }

  def "test find image by query and freeformTags"() {
    setup:
    def imageProvider = Mock(OracleImageProvider)
    def imageLookupController = new OracleImageLookupController(imageProvider)
    def reqTags1And2 = mockHttpServletRequest(["tag:tag1": "value1", "tag:Tag2": "Value2"])
    def reqTag3 = mockHttpServletRequest(["tag:tag3": "value3"])

    def images = [
      new OracleImage(id: "ocid.image.foo.notags",
        name: "foo 1",
        compatibleShapes: ["small"]),
      new OracleImage(id: "ocid.image.foo.alltags",
        name: "foo 2",
        compatibleShapes: ["small"],
        freeformTags: ["tag1":"value1", "tag2":"value2", "tag3":"value3"]),
      new OracleImage(id: "ocid.image.foo.tags1and3",
        name: "foo 3",
        compatibleShapes: ["small"],
        freeformTags: ["tag1":"value1", "tag3":"value3"]),
      new OracleImage(id: "ocid.image.bar.tags1and2",
        name: "bar 1",
        compatibleShapes: ["small"],
        freeformTags: ["tag1":"value1", "tag2":"value2"]
      )
    ]

    when:
    def resultsFooTags1And2 = imageLookupController.find("DEFAULT", "foo", reqTags1And2)
    def resultsNoPatternTags1And2 = imageLookupController.find("DEFAULT", "*", reqTags1And2)
    def resultsNoPatternTag3 = imageLookupController.find("DEFAULT", "*", reqTag3)

    then:
    3 * imageProvider.getAll() >> images
    resultsFooTags1And2.size() == 1
    resultsFooTags1And2[0].id == "ocid.image.foo.alltags"

    resultsNoPatternTags1And2.size() == 2
    resultsNoPatternTags1And2.find {it.id == "ocid.image.foo.alltags"} != null
    resultsNoPatternTags1And2.find {it.id == "ocid.image.bar.tags1and2"} != null

    resultsNoPatternTag3.size() == 2
    resultsNoPatternTag3.find {it.id == "ocid.image.foo.alltags"} != null
    resultsNoPatternTag3.find {it.id == "ocid.image.foo.tags1and3"} != null
  }

  private HttpServletRequest mockHttpServletRequest(Map<String, String> tagFilters) {
    return Mock(HttpServletRequest) {
      getParameterNames() >> {
        new Vector(tagFilters ? tagFilters.keySet() : []).elements()
      }
      getParameter(_ as String) >> {
        String key -> tagFilters?.get(key)
      }
    }
  }
}
