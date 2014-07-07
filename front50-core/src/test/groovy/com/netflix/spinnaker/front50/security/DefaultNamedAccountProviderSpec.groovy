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



package com.netflix.spinnaker.front50.security

import com.netflix.spinnaker.front50.model.application.Application
import groovy.transform.Immutable
import spock.lang.Shared
import spock.lang.Specification

@SuppressWarnings("GroovyMissingReturnStatement")
class DefaultNamedAccountProviderSpec extends Specification {

  @Shared
  DefaultNamedAccountProvider provider = new DefaultNamedAccountProvider()

  Void "have account name in list after being added"() {
    setup:
    def accountName = "account"
    def account = new TestNamedAccount(accountName, [:])

    when:
    provider.put account

    then:
    provider.accountNames.contains accountName
  }

  Void "be able to get account object by name"() {
    setup:
    def accountName = "account"
    def credentials = [key: "1234"]
    def account = new TestNamedAccount(accountName, credentials)

    when:
    provider.put account

    then:
    provider.get(accountName).is(account)
  }

  Void "be able to remove an account by name"() {
    setup:
    def accountName = "account"
    def account = new TestNamedAccount(accountName, [:])
    provider.put account

    when:
    provider.remove accountName

    then:
    !provider.get(accountName)
  }

  @Immutable
  static class TestNamedAccount implements NamedAccount<Map> {
    String name
    Map credentials

    @Override
    Class<Map> getType() {
      Map
    }

    @Override
    Application getApplication() {
      null
    }
  }
}
