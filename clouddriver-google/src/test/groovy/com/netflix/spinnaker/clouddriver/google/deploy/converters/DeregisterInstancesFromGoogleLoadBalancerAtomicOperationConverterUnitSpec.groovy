/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.deploy.converters

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.google.deploy.description.DeregisterInstancesFromGoogleLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.google.deploy.ops.DeregisterInstancesFromGoogleLoadBalancerAtomicOperation
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Shared
import spock.lang.Specification

class DeregisterInstancesFromGoogleLoadBalancerAtomicOperationConverterUnitSpec extends Specification {
  private static final SERVER_GROUP_NAME = "spinnaker-test-v000"
  private static final ZONE = "us-central1-b"
  private static final ACCOUNT_NAME = "auto"
  private static final INSTANCE_IDS = ["my-app7-dev-v000-instance1", "my-app7-dev-v000-instance2"]

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Shared
  DeregisterInstancesFromGoogleLoadBalancerAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new DeregisterInstancesFromGoogleLoadBalancerAtomicOperationConverter(objectMapper: mapper)
    def accountCredentialsProvider = Mock(AccountCredentialsProvider)
    def mockCredentials = Mock(GoogleNamedAccountCredentials)
    accountCredentialsProvider.getCredentials(_) >> mockCredentials
    converter.accountCredentialsProvider = accountCredentialsProvider
  }

  void "deregisterInstancesFromGoogleLoadBalancerDescription type returns correct description and operation types"() {
    setup:
      def input = [serverGroupName: SERVER_GROUP_NAME,
                   instanceIds: INSTANCE_IDS,
                   zone: ZONE,
                   accountName: ACCOUNT_NAME]

    when:
      def description = converter.convertDescription(input)
    then:
      description instanceof DeregisterInstancesFromGoogleLoadBalancerDescription

    when:
      def operation = converter.convertOperation(input)
    then:
      operation instanceof DeregisterInstancesFromGoogleLoadBalancerAtomicOperation
  }
}
