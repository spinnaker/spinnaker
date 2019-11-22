/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.fiat.model.resources

import spock.lang.Specification

class ServiceAccountSpec extends Specification {

  def 'should convert to UserPermission, filtering non-text strings'() {
    setup:
    ServiceAccount acct = new ServiceAccount().setName("my-svc-acct")
                                              .setMemberOf(["foo", "bar", "", "   "])

    when:
    def result = acct.toUserPermission()

    then:
    result.id == "my-svc-acct"
    result.roles.containsAll([
        new Role("foo").setSource(Role.Source.EXTERNAL),
        new Role("bar").setSource(Role.Source.EXTERNAL)
    ])
  }
}
