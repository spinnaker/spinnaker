/*
 * Copyright 2014 Netflix, Inc.
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


package com.netflix.spinnaker.orca.front50.model

import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class ApplicationSpec extends Specification {
  @Subject
  def application = new Application(accounts: "prod,test")

  @Unroll
  void "'#accounts' should expand to #expected"() {
    setup:
    application.accounts = accounts

    expect:
    application.listAccounts() == expected

    where:
    accounts    | expected
    "prod,test" | ["prod", "test"] as Set
    ""          | [] as Set
    null        | [] as Set
  }

  @Unroll
  void "#accounts should reduce to '#expected'"() {
    setup:
    application.updateAccounts(accounts as Set)

    expect:
    application.accounts == expected

    where:
    accounts                  | expected
    ["prod", "test", "extra"] | "prod,test,extra"
    ["prod"]                  | "prod"
    []                        | null
    null                      | null
  }

  void 'dynamic properties should be marshalled at root of application'() {
    setup:
    def mapper = OrcaObjectMapper.newInstance()
    application.someBoolean = true
    application.someMap = [ a: 'some string', b: 4 ]
    def expected = [
        accounts: "prod,test",
        requiredGroupMembership: [],
        someBoolean: true,
        someMap: [a: 'some string', b: 4]
    ]

    def applicationString = mapper.writeValueAsString(application)
    def applicationAsMap = mapper.readValue(applicationString, Map)

    expect:
    application.details().someBoolean == true
    application.details().someMap == expected.someMap
    applicationAsMap == expected
  }
}
