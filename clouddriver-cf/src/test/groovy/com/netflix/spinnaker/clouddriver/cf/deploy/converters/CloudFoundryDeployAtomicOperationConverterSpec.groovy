/*
 * Copyright 2015 Pivotal, Inc.
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
package com.netflix.spinnaker.clouddriver.cf.deploy.converters
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.cf.TestCredential
import com.netflix.spinnaker.clouddriver.cf.deploy.description.CloudFoundryDeployDescription
import com.netflix.spinnaker.clouddriver.cf.security.CloudFoundryAccountCredentials
import com.netflix.spinnaker.clouddriver.deploy.DeployAtomicOperation
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import spock.lang.Shared
import spock.lang.Specification
/**
 * Test cases for {@link CloudFoundryDeployAtomicOperationConverter}
 *
 *
 */
class CloudFoundryDeployAtomicOperationConverterSpec extends Specification {

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  private static final ACCOUNT_NAME = "test"

  @Shared
  CloudFoundryDeployAtomicOperationConverter converter

  def setupSpec() {
    def credentialsRepo = new MapBackedAccountCredentialsRepository()

    credentialsRepo.save(ACCOUNT_NAME, TestCredential.named(ACCOUNT_NAME))

    def accountCredentialsProvider = Stub(AccountCredentialsProvider) {
      getCredentials(ACCOUNT_NAME) >> Stub(CloudFoundryAccountCredentials)
    }
    this.converter = new CloudFoundryDeployAtomicOperationConverter(objectMapper: mapper,
        accountCredentialsProvider: accountCredentialsProvider,
        repository: credentialsRepo
    )
  }

  def "should return CloudFoundryDeployDescription and DeployAtomicOperation"() {
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

  def "should support templated repositories"() {
    setup:
    def input = [application: 'test-app',
                 repository: '/{{job}}-{{buildNumber}}',
                 artifact: 'test-app-0.0.1-BUILD-SNAPSHOT.war',
                 trigger: [job: 'test-job', buildNumber: 123],
                 credentials: 'test']

    when:
    def description = converter.convertDescription(input)

    then:
    description.repository == "/${input.trigger.job}-${input.trigger.buildNumber}"

  }

  def "should handle lack of trigger parameters"() {
    setup:
    def input = [application: 'test-app',
                 repository: '/{{job}}-{{buildNumber}}',
                 artifact: 'test-app-0.0.1-BUILD-SNAPSHOT.war',
                 credentials: 'test']

    when:
    def description = converter.convertDescription(input)

    then:
    description.repository == '/{{job}}-{{buildNumber}}'
  }

  def "should handle array of load balancers"() {
    setup:
    def input = [
        application: 'test-app',
        repository: '/{{job}}-{{buildNumber}}',
        artifact: 'test-app-0.0.1-BUILD-SNAPSHOT.war',
        loadBalancers: ['host1', 'host2'],
        credentials: 'test'
    ]

    when:
    def description = converter.convertDescription(input)

    then:
    description.loadBalancers == 'host1,host2'
  }

  def "should handle comma-delimited list of load balancers"() {
    setup:
    def input = [
        application: 'test-app',
        repository: '/{{job}}-{{buildNumber}}',
        artifact: 'test-app-0.0.1-BUILD-SNAPSHOT.war',
        loadBalancers: 'host1,host2',
        credentials: 'test'
    ]

    when:
    def description = converter.convertDescription(input)

    then:
    description.loadBalancers == 'host1,host2'
  }


}
