/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.core.limits

import spock.lang.Specification

/**
 * ServiceLimitConfigurationSpec.
 */
class ServiceLimitConfigurationSpec extends Specification {

  def 'should accept all null filters'() {
    given:
    ServiceLimitConfiguration cfg = new ServiceLimitConfigurationBuilder()
      .withDefault(limit, configuredDefaultsVal)
      .build()

    expect:
    cfg.getLimit(limit, null, null, null, suppliedDefaultsVal) == configuredDefaultsVal

    where:
    limit = 'foo'
    configuredDefaultsVal = 1.0d
    suppliedDefaultsVal = 2.0d
  }

  def 'should override values'() {
    given:
    ServiceLimitConfiguration cfg = new ServiceLimitConfigurationBuilder()
      .withDefault(limit, defaultLimit)
      .withCloudProviderOverride(cloudProvider, limit, cloudProviderLimit)
      .withAccountOverride(accountA, limit, accountALimit)
      .withAccountOverride(accountB, limit, accountBLimit)
      .withImplementationDefault(implementation, limit, implementationDefault)
      .withImplementationAccountOverride(implementation, implementationAccount, limit, implementationAccountLimit)
      .build()


    expect:
    cfg.getLimit(limit, implementationName, accountName, cloudProviderName, defaultValue) == expectedValue

    where:
    defaultValue = 0.5d
    limit = 'foo'
    defaultLimit = 1.0d
    cloudProvider = 'cloudProvider'
    cloudProviderLimit = 2.0d
    accountA = 'accountA'
    accountALimit = 3.0d
    accountB = 'accountB'
    accountBLimit = 3.5d
    implementation = 'implementation'
    implementationDefault = 4.0d
    implementationAccount = 'accountA'
    implementationAccountLimit = 5.0d



    limitName | accountName | cloudProviderName | implementationName | expectedValue
    'foo' | 'accountC'| 'cloudProvider' | 'implementationB' | 2.0d
    'foo' | 'accountB'| 'cloudProvider' | 'implementationB' | 3.5d
    'foo' | 'accountB'| 'cloudProvider' | 'implementation'  | 4.0d
    'foo' | 'accountA'| 'cloudProvider' | 'implementation'  | 5.0d
  }
}
