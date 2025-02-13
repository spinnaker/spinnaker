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

package com.netflix.spinnaker.clouddriver.google.controllers

import com.google.api.services.compute.model.Image
import spock.lang.Specification
import spock.lang.Unroll

import jakarta.servlet.http.HttpServletRequest

class GoogleNamedImageLookupControllerSpec extends Specification {
  void "should extract tags from query parameters"() {
    given:
      def httpServletRequest = httpServletRequest(["tag:tag1": "value1", "tag:Tag2": "value2"])

    expect:
      GoogleNamedImageLookupController.extractTagFilters(httpServletRequest) == ["tag1": "value1", "tag2": "value2"]
  }

  void "should support filtering on 1..* tags"() {
    given:
      def namedImage1 = new GoogleNamedImageLookupController.NamedImage(null, "image-123", null, [state: "released"])

      def namedImage2 = new GoogleNamedImageLookupController.NamedImage(null, "image-456", null, [state: "released", engine: "spinnaker"])

    and:
      def controller = new GoogleNamedImageLookupController(null)

    expect:
      // single tag ... matches all
      controller.filter([namedImage1, namedImage2], [state: "released"]) == [namedImage1, namedImage2]

      // multiple tags ... matches one (case-insensitive)
      controller.filter([namedImage1, namedImage2], [STATE: "released", engine: "SpinnakeR"]) == [namedImage2]

      // single tag ... matches none
      controller.filter([namedImage1, namedImage2], [xxx: "released"]) == []

      // no tags ... matches all
      controller.filter([namedImage1, namedImage2], [:]) == [namedImage1, namedImage2]
  }

  @Unroll
  void "should build tags map from description and labels"() {
    given:
      def image = new Image(description: description, labels: labels)

    expect:
      GoogleNamedImageLookupController.buildTagsMap(image) == tagsMap

    where:
      description                                                         | labels                                   || tagsMap
      "appversion: someAppVersion"                                        | null                                     || [
                                                                                                                          appversion: "someAppVersion"
                                                                                                                        ]
      "appversion: someAppVersion, build_host: http://somebuildhost:8080" | null                                     || [
                                                                                                                          appversion: "someAppVersion",
                                                                                                                          build_host: "http://somebuildhost:8080"
                                                                                                                        ]
      "appversion: someAppVersion, build_host: http://somebuildhost:8080" | [state: "released", engine: "spinnaker"] || [
                                                                                                                          appversion: "someAppVersion",
                                                                                                                          build_host: "http://somebuildhost:8080",
                                                                                                                          state: "released",
                                                                                                                          engine: "spinnaker"
                                                                                                                        ]
      "appversion: version1, appversion: version2"                        |  null                                    || [
                                                                                                                          appversion: "version2"
                                                                                                                        ]
      null                                                                | [state: "released", engine: "spinnaker"] || [
                                                                                                                          state: "released",
                                                                                                                          engine: "spinnaker"
                                                                                                                        ]
      "non key-value pair"                                                | null                                     || [:]
      "non key-value pair 1, non key-value pair 2"                        | null                                     || [:]
      null                                                                | null                                     || [:]
      null                                                                | [:]                                      || [:]
      ""                                                                  | null                                     || [:]
      ""                                                                  | [:]                                      || [:]
  }

  private HttpServletRequest httpServletRequest(Map<String, String> tagFilters) {
    return Mock(HttpServletRequest) {
      getParameterNames() >> {
        new Vector(["param1"] + tagFilters.keySet()).elements()
      }
      getParameter(_) >> { String key -> tagFilters.get(key) }
    }
  }
}
