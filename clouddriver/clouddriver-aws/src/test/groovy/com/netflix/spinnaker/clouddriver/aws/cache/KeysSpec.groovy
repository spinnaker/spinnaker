/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.cache

import spock.lang.Specification
import spock.lang.Unroll

class KeysSpec extends Specification {

  @Unroll
  def 'key fields match namespace fields if present'() {

    expect:
    Keys.parse(key).keySet() == namespace.fields

    where:

    key                                                                          | namespace
    "aws:securityGroups:app-stack-detail:sg-12345:us-west-2:0123456789:vpc-1234" | Keys.Namespace.SECURITY_GROUPS
  }

  @Unroll
  def 'parse security group keys with special characters'() {
    given:
    def parsedKey = Keys.parse(key)

    expect:
    with(parsedKey) {
      name == expectedName
      application == expectedApp
    }

    where:

    key                                                                          || expectedName       | expectedApp
    "aws:securityGroups:app-stack-detail:sg-12345:us-west-2:0123456789:vpc-1234" || 'app-stack-detail' | 'app'
    "aws:securityGroups:app:stack%detail:sg-12345:us-west-2:0123456789:vpc-1234" || 'app:stack%detail' | null
    "aws:securityGroups:app:stack:detail:sg-12345:us-west-2:0123456789:vpc-1234" || 'app:stack:detail' | null
    "aws:securityGroups:app%stack%detail:sg-12345:us-west-2:0123456789:vpc-1234" || 'app%stack%detail' | null
    "aws:securityGroups:app%stack:detail:sg-12345:us-west-2:0123456789:vpc-1234" || 'app%stack:detail' | null
  }
}
