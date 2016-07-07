/*
 * Copyright 2016 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cf.security

import spock.lang.Specification

class CloudFoundryAccountCredentialsSpec extends Specification {

  void "verify basic behavior"() {
    given:
    def username = 'my-username'
    def password = 'my-password'
    def org = 'my-org'
    def space = 'my-space'

    when:
    def credentials = new CloudFoundryAccountCredentials(username: username, password: password, org: org, space: space)
    def cloudCredentials = credentials.credentials

    then:
    cloudCredentials.email == username
    cloudCredentials.password == password
    credentials.cloudProvider == 'cf'
    credentials.requiredGroupMembership == []
    credentials.regions == [(org): [space]]
  }

}
