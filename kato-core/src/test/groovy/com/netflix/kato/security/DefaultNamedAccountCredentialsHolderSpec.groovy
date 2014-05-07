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

package com.netflix.kato.security

import spock.lang.Shared
import spock.lang.Specification

class DefaultNamedAccountCredentialsHolderSpec extends Specification {

  @Shared
  DefaultNamedAccountCredentialsHolder credentialsHolder

  def setup() {
    this.credentialsHolder = new DefaultNamedAccountCredentialsHolder()
  }

  void "credentials are able to be saved and retrieved"() {
    setup:
    def credentials = Mock(NamedAccountCredentials)
    credentialsHolder.put("test", credentials)

    when:
    def c1 = credentialsHolder.getCredentials("test")

    then:
    c1.is credentials
  }

  void "credential names are retrievable"() {
    setup:
    def credentials = Mock(NamedAccountCredentials)
    credentialsHolder.put("test", credentials)

    when:
    def l = credentialsHolder.accountNames

    then:
    l == ["test"]
  }
}
