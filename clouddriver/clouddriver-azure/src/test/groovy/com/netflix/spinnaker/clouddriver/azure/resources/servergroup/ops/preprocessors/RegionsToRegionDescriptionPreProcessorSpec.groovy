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

package com.netflix.spinnaker.clouddriver.azure.resources.servergroup.ops.preprocessors

import spock.lang.Specification
import spock.lang.Unroll

class RegionsToRegionDescriptionPreProcessorSpec extends Specification {
  @Unroll
  void "should convert legacy descriptions"() {
    setup:
    def preProcessor = new RegionsToRegionDescriptionPreProcessor()
    def legacyDescription = [
      region : region,
      regions: regions
    ]

    when:
    def processedDescription = preProcessor.process(legacyDescription)

    then:
    processedDescription == [
      region: expectedRegion
    ]

    where:
    region   | regions    | expectedRegion
    "useast" | null       | "useast"
    null     | ["useast"] | "useast"
    ""       | ["useast"] | "useast"

    // If multiple regions are passed, select the first one.
    null     | ["uswest", "useast"] | "uswest"

    // `region` takes precedence over `regions`.
    "uswest" | ["useast"]           | "uswest"
  }
}
