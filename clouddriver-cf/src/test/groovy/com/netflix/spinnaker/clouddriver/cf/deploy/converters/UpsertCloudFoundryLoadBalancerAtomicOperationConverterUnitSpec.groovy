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
import com.netflix.spinnaker.clouddriver.cf.security.CloudFoundryAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.cf.deploy.description.UpsertCloudFoundryLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.cf.deploy.ops.loadbalancer.UpsertCloudFoundryLoadBalancerAtomicOperation
import spock.lang.Shared
import spock.lang.Specification

class UpsertCloudFoundryLoadBalancerAtomicOperationConverterUnitSpec extends Specification {

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Shared
  UpsertCloudFoundryLoadBalancerAtomicOperationConverter converter

  def setupSpec() {
    def accountCredentialsProvider = Stub(AccountCredentialsProvider) {
      getCredentials('test') >> Stub(CloudFoundryAccountCredentials)
    }
    converter = new UpsertCloudFoundryLoadBalancerAtomicOperationConverter(objectMapper: mapper,
        accountCredentialsProvider: accountCredentialsProvider
    )
  }

  def "should return UpsertCloudFoundryLoadBalancerDescription and UpsertCloudFoundryLoadBalancerAtomicOperation"() {
    setup:
    def input = [loadBalancerName: 'load-balancer', region: 'some-region', zone: 'some-zone',
                 credentials: 'test']

    when:
    def description = converter.convertDescription(input)

    then:
    description instanceof UpsertCloudFoundryLoadBalancerDescription

    when:
    def operation = converter.convertOperation(input)

    then:
    operation instanceof UpsertCloudFoundryLoadBalancerAtomicOperation
  }

  void "should not fail to serialize unknown properties"() {
    setup:
    def application = "load-balancer"
    def region = 'some-region'
    def zone = 'some-zone'
    def input = [loadBalancerName: application, region: region, zone: zone,
                 unknownProp: "this",
                 credentials: 'test']

    when:
    def description = converter.convertDescription(input)

    then:
    description.loadBalancerName == application
    description.region == region
    description.zone == zone
  }

}
