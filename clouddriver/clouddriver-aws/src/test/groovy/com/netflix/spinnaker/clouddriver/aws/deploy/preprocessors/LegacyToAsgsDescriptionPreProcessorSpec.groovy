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

class LegacyToAsgsDescriptionPreProcessorSpec extends Specification {
  @Unroll
  void "should convert legacy descriptions"() {
    setup:
    def preProcessor = new LegacyToAsgsDescriptionPreProcessor()
    def legacyDescription = [
      asgName        : asgName,
      serverGroupName: serverGroupName,
      region         : region,
      regions        : regions,
      asgs           : asgs
    ]

    when:
    def processedDescription = preProcessor.process(legacyDescription)

    then:
    processedDescription == [
      asgs: expectedAsgs
    ]

    where:
    asgName  | serverGroupName | region      | regions       | asgs | expectedAsgs
    "s-v001" | null            | "us-east-1" | null          | null | [[serverGroupName: "s-v001", region: "us-east-1"]]
    "s-v001" | null            | "us-east-1" | []            | null | [[serverGroupName: "s-v001", region: "us-east-1"]]
    "s-v001" | null            | null        | ["us-east-1"] | null | [[serverGroupName: "s-v001", region: "us-east-1"]]
    "s-v001" | ""              | "us-east-1" | null          | null | [[serverGroupName: "s-v001", region: "us-east-1"]]
    "s-v001" | ""              | "us-east-1" | []            | null | [[serverGroupName: "s-v001", region: "us-east-1"]]
    "s-v001" | ""              | null        | ["us-east-1"] | null | [[serverGroupName: "s-v001", region: "us-east-1"]]
    null     | "s-v001"        | "us-east-1" | null          | null | [[serverGroupName: "s-v001", region: "us-east-1"]]
    null     | "s-v001"        | "us-east-1" | []            | null | [[serverGroupName: "s-v001", region: "us-east-1"]]
    null     | "s-v001"        | null        | ["us-east-1"] | null | [[serverGroupName: "s-v001", region: "us-east-1"]]
    ""       | "s-v001"        | "us-east-1" | null          | null | [[serverGroupName: "s-v001", region: "us-east-1"]]
    ""       | "s-v001"        | "us-east-1" | []            | null | [[serverGroupName: "s-v001", region: "us-east-1"]]
    ""       | "s-v001"        | null        | ["us-east-1"] | null | [[serverGroupName: "s-v001", region: "us-east-1"]]

    // Multiple regions are supported.
    "s-v001" | null            | null        | ["us-east-1", "us-west-1"] | null                                                                                                 | [[serverGroupName: "s-v001", region: "us-east-1"], [serverGroupName: "s-v001", region: "us-west-1"]]

    // Passed asgs take precedence.
    "s-v001" | null            | null        | ["us-east-1", "us-west-1"] | [[serverGroupName: "s-v002", region: "us-west-1"]]                                                   | [[serverGroupName: "s-v002", region: "us-west-1"]]
    null     | null            | null        | null                       | [[serverGroupName: "s-v002", region: "us-east-1"], [serverGroupName: "s-v002", region: "us-west-1"]] | [[serverGroupName: "s-v002", region: "us-east-1"], [serverGroupName: "s-v002", region: "us-west-1"]]
  }
}
