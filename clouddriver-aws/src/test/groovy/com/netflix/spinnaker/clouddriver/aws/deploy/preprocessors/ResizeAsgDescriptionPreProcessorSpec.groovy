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

package com.netflix.spinnaker.clouddriver.aws.deploy.preprocessors

import spock.lang.Specification
import spock.lang.Unroll

class ResizeAsgDescriptionPreProcessorSpec extends Specification {
  @Unroll
  void "should convert legacy descriptions, with capacity"() {
    setup:
    def preProcessor = new ResizeAsgDescriptionPreProcessor()
    def legacyDescription = [
      asgName        : asgName,
      serverGroupName: serverGroupName,
      region         : region,
      regions        : regions,
      capacity       : capacity,
      asgs           : asgs
    ]

    when:
    def processedDescription = preProcessor.process(legacyDescription)

    then:
    processedDescription == [
      asgs: expectedAsgs
    ]

    where:
    asgName  | serverGroupName | region      | regions       | capacity                     | asgs | expectedAsgs
    "s-v001" | null            | "us-east-1" | null          | [min: 1, desired: 2, max: 3] | null | [[serverGroupName: "s-v001", region: "us-east-1", capacity: [min: 1, desired: 2, max: 3]]]
    "s-v001" | null            | "us-east-1" | []            | [min: 1, desired: 2, max: 3] | null | [[serverGroupName: "s-v001", region: "us-east-1", capacity: [min: 1, desired: 2, max: 3]]]
    "s-v001" | null            | null        | ["us-east-1"] | [min: 1, desired: 2, max: 3] | null | [[serverGroupName: "s-v001", region: "us-east-1", capacity: [min: 1, desired: 2, max: 3]]]
    "s-v001" | ""              | "us-east-1" | null          | [min: 1, desired: 2, max: 3] | null | [[serverGroupName: "s-v001", region: "us-east-1", capacity: [min: 1, desired: 2, max: 3]]]
    "s-v001" | ""              | "us-east-1" | []            | [min: 1, desired: 2, max: 3] | null | [[serverGroupName: "s-v001", region: "us-east-1", capacity: [min: 1, desired: 2, max: 3]]]
    "s-v001" | ""              | null        | ["us-east-1"] | [min: 1, desired: 2, max: 3] | null | [[serverGroupName: "s-v001", region: "us-east-1", capacity: [min: 1, desired: 2, max: 3]]]
    null     | "s-v001"        | "us-east-1" | null          | [min: 1, desired: 2, max: 3] | null | [[serverGroupName: "s-v001", region: "us-east-1", capacity: [min: 1, desired: 2, max: 3]]]
    null     | "s-v001"        | "us-east-1" | []            | [min: 1, desired: 2, max: 3] | null | [[serverGroupName: "s-v001", region: "us-east-1", capacity: [min: 1, desired: 2, max: 3]]]
    null     | "s-v001"        | null        | ["us-east-1"] | [min: 1, desired: 2, max: 3] | null | [[serverGroupName: "s-v001", region: "us-east-1", capacity: [min: 1, desired: 2, max: 3]]]
    ""       | "s-v001"        | "us-east-1" | null          | [min: 1, desired: 2, max: 3] | null | [[serverGroupName: "s-v001", region: "us-east-1", capacity: [min: 1, desired: 2, max: 3]]]
    ""       | "s-v001"        | "us-east-1" | []            | [min: 1, desired: 2, max: 3] | null | [[serverGroupName: "s-v001", region: "us-east-1", capacity: [min: 1, desired: 2, max: 3]]]
    ""       | "s-v001"        | null        | ["us-east-1"] | [min: 1, desired: 2, max: 3] | null | [[serverGroupName: "s-v001", region: "us-east-1", capacity: [min: 1, desired: 2, max: 3]]]

    // Multiple regions are supported.
    "s-v001" | null            | null        | ["us-east-1", "us-west-1"] | [min: 1, desired: 2, max: 3] | null                                                                                                                                                                                 | [[serverGroupName: "s-v001", region: "us-east-1", capacity: [min: 1, desired: 2, max: 3]], [serverGroupName: "s-v001", region: "us-west-1", capacity: [min: 1, desired: 2, max: 3]]]

    // Passed asgs take precedence.
    "s-v001" | "s-v003"        | "us-west-2" | ["us-east-1", "us-west-1"] | [min: 7, desired: 8, max: 9] | [[serverGroupName: "s-v002", region: "us-west-1", capacity: [min: 1, desired: 2, max: 3]]]                                                                                           | [[serverGroupName: "s-v002", region: "us-west-1", capacity: [min: 1, desired: 2, max: 3]]]
    null     | null            | null        | null                       | null                         | [[serverGroupName: "s-v002", region: "us-east-1", capacity: [min: 1, desired: 2, max: 3]], [serverGroupName: "s-v002", region: "us-west-1", capacity: [min: 4, desired: 5, max: 6]]] | [[serverGroupName: "s-v002", region: "us-east-1", capacity: [min: 1, desired: 2, max: 3]], [serverGroupName: "s-v002", region: "us-west-1", capacity: [min: 4, desired: 5, max: 6]]]
  }

  void "should convert capacity constraints"() {
    def preProcessor = new ResizeAsgDescriptionPreProcessor()
    def legacyDescription = [
      asgName        : "s-v001",
      region         : "us-east-1",
      constraints : [
          capacity: [min: 1, max: 3, desired: 2]
      ]
    ]

    when:
    def processedDescription = preProcessor.process(legacyDescription)

    then:
    processedDescription.asgs[0].constraints == [
      capacity: [
        min    : 1,
        max    : 3,
        desired: 2
      ]
    ]
  }
}
