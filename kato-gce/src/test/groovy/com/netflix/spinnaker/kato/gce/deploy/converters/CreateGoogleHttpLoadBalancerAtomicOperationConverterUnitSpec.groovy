/*
 * Copyright 2014 Google, Inc.
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

package com.netflix.spinnaker.kato.gce.deploy.converters

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.amos.gce.GoogleNamedAccountCredentials
import com.netflix.spinnaker.kato.gce.deploy.description.CreateGoogleHttpLoadBalancerDescription
import com.netflix.spinnaker.kato.gce.deploy.ops.CreateGoogleHttpLoadBalancerAtomicOperation
import spock.lang.Shared
import spock.lang.Specification

import static com.netflix.spinnaker.kato.gce.deploy.CreateGoogleHttpLoadBalancerTestConstants.*

class CreateGoogleHttpLoadBalancerAtomicOperationConverterUnitSpec extends Specification {
  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Shared
  CreateGoogleHttpLoadBalancerAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new CreateGoogleHttpLoadBalancerAtomicOperationConverter(objectMapper: mapper)
    def accountCredentialsProvider = Mock(AccountCredentialsProvider)
    def mockCredentials = Mock(GoogleNamedAccountCredentials)
    accountCredentialsProvider.getCredentials(_) >> mockCredentials
    converter.accountCredentialsProvider = accountCredentialsProvider
  }

  void "createGoogleHttpLoadBalancerDescription type returns CreateGoogleHttpLoadBalancerDescription and CreateGoogleHttpLoadBalancerAtomicOperation"() {
    setup:
      def input = [
          loadBalancerName: LOAD_BALANCER_NAME,
          accountName: ACCOUNT_NAME,
          healthCheck: [checkIntervalSec: CHECK_INTERVAL_SEC],
          backends: [[group: INSTANCE_GROUP, balancingMode: BALANCING_MODE]],
          ipAddress: IP_ADDRESS,
          portRange: PORT_RANGE,
          hostRules: [[hosts: [HOST], pathMatcher: MATCHER]],
          pathMatchers: [[name: MATCHER, defaultService: SERVICE, pathRules: [[paths: [PATH], service: SERVICE]]]]
      ]

    when:
      def description = converter.convertDescription(input)

    then:
      description instanceof CreateGoogleHttpLoadBalancerDescription
      description.loadBalancerName == LOAD_BALANCER_NAME
      description.healthCheck.checkIntervalSec == 7
      description.healthCheck.timeoutSec == null
      description.backends.size() == 1
      description.backends.get(0).group == INSTANCE_GROUP
      description.backends.get(0).balancingMode == BALANCING_MODE
      description.ipAddress == IP_ADDRESS
      description.portRange == PORT_RANGE
      description.pathMatchers.size() == 1
      description.pathMatchers.get(0).defaultService == SERVICE
      description.pathMatchers.get(0).pathRules.size() == 1
      description.pathMatchers.get(0).pathRules.get(0).service == SERVICE
      description.pathMatchers.get(0).pathRules.get(0).paths.size() == 1
      description.pathMatchers.get(0).pathRules.get(0).paths.get(0) == PATH

    when:
      def operation = converter.convertOperation(input)

    then:
     operation instanceof CreateGoogleHttpLoadBalancerAtomicOperation
  }
}
