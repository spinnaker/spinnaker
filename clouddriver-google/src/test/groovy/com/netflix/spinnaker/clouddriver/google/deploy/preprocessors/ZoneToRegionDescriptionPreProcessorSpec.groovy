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

package com.netflix.spinnaker.clouddriver.google.deploy.preprocessors

import spock.lang.Specification
import spock.lang.Unroll

class ZoneToRegionDescriptionPreProcessorSpec extends Specification {
  @Unroll
  void "should convert legacy descriptions"() {
    setup:
      def preProcessor = new ZoneToRegionDescriptionPreProcessor()
      def legacyDescription = [
        zone  : zone,
        region: region
      ]

    when:
      def processedDescription = preProcessor.process(legacyDescription)

    then:
      processedDescription == [
        region: expectedRegion
      ]

    where:
      zone            | region        | expectedRegion
      "us-central1-f" | null          | "us-central1"
      "us-central1-f" | ""            | "us-central1"
      null            | "us-central1" | "us-central1"
      ""              | "us-central1" | "us-central1"

      // If both are passed, `region` takes precedence over `zone`.
      "us-central1-b" | "us-east1"    | "us-east1"
  }
}
