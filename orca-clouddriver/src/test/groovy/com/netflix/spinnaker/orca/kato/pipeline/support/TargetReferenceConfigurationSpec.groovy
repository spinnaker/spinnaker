/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.kato.pipeline.support

import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class TargetReferenceConfigurationSpec extends Specification {

  def "returns the correct location"() {
    setup:
    def subject = new TargetReferenceConfiguration(regions: regions, zones: zones)

    expect:
    subject.locations == want

    where:
    regions          | zones              || want
    ["east", "west"] | null               || ["east", "west"]
    ["east", "west"] | ["north", "south"] || ["east", "west"]
    []               | ["north", "south"] || ["north", "south"]
    null             | ["north", "south"] || ["north", "south"]
    null             | []                 || []
    null             | null               || []
  }

  def "sets cloudProvider when providerType is sent"() {
    setup:
    def subject = new TargetReferenceConfiguration(providerType: "someprovider")

    expect:
    subject.cloudProvider == "someprovider"
  }
}
