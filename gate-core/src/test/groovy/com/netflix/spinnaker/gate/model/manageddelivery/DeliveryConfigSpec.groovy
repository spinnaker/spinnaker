/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.gate.model.manageddelivery;

import com.fasterxml.jackson.databind.ObjectMapper
import spock.lang.Specification

class DeliveryConfigSpec extends Specification {

  private ObjectMapper mapper = new ObjectMapper()

  def "apiVersion is optional"() {
    given:
    def manifest = [
      name          : "my-manifest",
      application   : "fnord",
      serviceAccount: "gate@spinnaker.io"
    ]
    def json = mapper.writeValueAsString(manifest)

    when:
    def deserialized = mapper.readValue(json, DeliveryConfig.class)

    then:
    noExceptionThrown()

    and:
    deserialized.apiVersion == null
  }

  def "apiVersion can be specified"() {
    given:
    def manifest = [
      apiVersion    : "spinnaker.keel/v2",
      name          : "my-manifest",
      application   : "fnord",
      serviceAccount: "gate@spinnaker.io"
    ]
    def json = mapper.writeValueAsString(manifest)

    when:
    def deserialized = mapper.readValue(json, DeliveryConfig.class)

    then:
    noExceptionThrown()

    and:
    deserialized.apiVersion == manifest.apiVersion
  }
}
