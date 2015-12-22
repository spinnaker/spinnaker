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

package com.netflix.spinnaker.clouddriver.aws.deploy.converters

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.aws.deploy.converters.ModifyAsgAtomicOperationConverter
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class ModifyAsgAtomicOperationConverterUnitSpec extends Specification {

  @Shared
  ModifyAsgAtomicOperationConverter converter

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  void setupSpec() {
    def accountCredentialsProvider = Stub(AccountCredentialsProvider) {
      getCredentials('test') >> Stub(NetflixAmazonCredentials)
    }
    this.converter = new ModifyAsgAtomicOperationConverter(objectMapper: mapper, accountCredentialsProvider: accountCredentialsProvider)
  }

  void 'moves "Default" termination policy to end of list, if present'() {
    given:
    def input = [
        asgs: [ [asgName: "asg-v001", region: "us-west-1"] ],
        terminationPolicies: ["Default", "NewestInstance", "OldestInstance"],
        credentials: "test"
    ]

    when:
    def description = converter.convertDescription(input)

    then:
    description.terminationPolicies == ["NewestInstance", "OldestInstance", "Default"]
  }

  @Unroll
  void 'leaves termination policies unchanged if "Default" is last or not present'() {
    given:
    def input = [
        asgs: [ [asgName: "asg-v001", region: "us-west-1"] ],
        terminationPolicies: policies,
        credentials: "test"
    ]

    when:
    def description = converter.convertDescription(input)

    then:
    description.terminationPolicies == expected

    where:
    policies              || expected
    ["Default"]           || ["Default"]
    ["Default", "Other"]  || ["Other", "Default"]
    ["A", "B"]            || ["A", "B"]
    ["B", "A"]            || ["B", "A"]
    []                    || []
  }
}
