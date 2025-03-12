/*
 * Copyright 2019 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.description

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import spock.lang.Specification

class KubernetesAtomicOperationDescriptionSpec extends Specification {
  def objectMapper = new ObjectMapper()
    .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  def "correctly deserializes the account field"() {
    when:
    def accountName = "my-k8s-account"
    def input = [
      account: accountName
    ]
    def output = objectMapper.convertValue(input, KubernetesAtomicOperationDescription.class)

    then:
    output.getAccount() == accountName
  }
}
