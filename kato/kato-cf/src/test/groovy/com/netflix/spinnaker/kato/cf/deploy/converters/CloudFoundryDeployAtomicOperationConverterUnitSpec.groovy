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
package com.netflix.spinnaker.kato.cf.deploy.converters

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.kato.cf.deploy.description.CloudFoundryDeployDescription
import com.netflix.spinnaker.kato.cf.security.CloudFoundryAccountCredentials
import com.netflix.spinnaker.kato.deploy.DeployAtomicOperation
import spock.lang.Shared
import spock.lang.Specification

/**
 * Test cases for {@link CloudFoundryDeployAtomicOperationConverter}
 *
 *
 */
class CloudFoundryDeployAtomicOperationConverterUnitSpec extends Specification {

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Shared
  CloudFoundryDeployAtomicOperationConverter converter

  def setupSpec() {
    def accountCredentialsProvider = Stub(AccountCredentialsProvider) {
      getCredentials('test') >> Stub(CloudFoundryAccountCredentials)
    }
    this.converter = new CloudFoundryDeployAtomicOperationConverter(objectMapper: mapper,
        accountCredentialsProvider: accountCredentialsProvider
    )
  }

  def "cloudFoundryDeployDescription type returns CloudFoundryDeployDescription and DeployAtomicOperation"() {
    setup:
    def input = [application: 'test-app', artifact: 'test-app-0.0.1-BUILD-SNAPSHOT.war',
                 credentials: 'test']

    when:
    def description = converter.convertDescription(input)

    then:
    description instanceof CloudFoundryDeployDescription

    when:
    def operation = converter.convertOperation(input)

    then:
    operation instanceof DeployAtomicOperation
  }

  void "should not fail to serialize unknown properties"() {
    setup:
    def input = [application: application, unknownProp: "this", credentials: 'test']

    when:
    def description = converter.convertDescription(input)

    then:
    description.application == application

    where:
    application = "kato"
  }

}
