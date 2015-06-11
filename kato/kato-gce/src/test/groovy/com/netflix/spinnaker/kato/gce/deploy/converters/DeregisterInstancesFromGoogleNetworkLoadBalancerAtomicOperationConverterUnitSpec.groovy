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

package com.netflix.spinnaker.kato.gce.deploy.converters

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.amos.gce.GoogleNamedAccountCredentials
import com.netflix.spinnaker.kato.gce.deploy.description.DeregisterInstancesFromGoogleNetworkLoadBalancerDescription
import com.netflix.spinnaker.kato.gce.deploy.ops.DeregisterInstancesFromGoogleNetworkLoadBalancerAtomicOperation
import spock.lang.Shared
import spock.lang.Specification

class DeregisterInstancesFromGoogleNetworkLoadBalancerAtomicOperationConverterUnitSpec extends Specification {
  private static final REPLICA_POOL_NAME = "spinnaker-test-v000"
  private static final ZONE = "us-central1-b"
  private static final ACCOUNT_NAME = "auto"
  private static final INSTANCE_IDS = ["my-app7-dev-v000-instance1", "my-app7-dev-v000-instance2"]

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Shared
  DeregisterInstancesFromGoogleNetworkLoadBalancerAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new DeregisterInstancesFromGoogleNetworkLoadBalancerAtomicOperationConverter(objectMapper: mapper)
    def accountCredentialsProvider = Mock(AccountCredentialsProvider)
    def mockCredentials = Mock(GoogleNamedAccountCredentials)
    accountCredentialsProvider.getCredentials(_) >> mockCredentials
    converter.accountCredentialsProvider = accountCredentialsProvider
  }

  void "deregisterInstancesFromGoogleNetworkLoadBalancerDescription type returns correct description and operation types"() {
    setup:
      def input = [replicaPoolName: REPLICA_POOL_NAME,
                   instanceIds: INSTANCE_IDS,
                   zone: ZONE,
                   accountName: ACCOUNT_NAME]

    when:
      def description = converter.convertDescription(input)
    then:
      description instanceof DeregisterInstancesFromGoogleNetworkLoadBalancerDescription

    when:
      def operation = converter.convertOperation(input)
    then:
      operation instanceof DeregisterInstancesFromGoogleNetworkLoadBalancerAtomicOperation
  }
}
