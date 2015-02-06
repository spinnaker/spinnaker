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
import com.netflix.spinnaker.kato.gce.deploy.description.DeleteGoogleHttpLoadBalancerDescription
import com.netflix.spinnaker.kato.gce.deploy.ops.DeleteGoogleHttpLoadBalancerAtomicOperation
import com.netflix.spinnaker.amos.gce.GoogleNamedAccountCredentials
import spock.lang.Shared
import spock.lang.Specification

class DeleteGoogleHttpLoadBalancerAtomicOperationConverterUnitSpec extends Specification {
  private static final long TIMEOUT_SECONDS = 5
  private static final String HTTP_LOAD_BALANCER_NAME = "spinnaker-test-v000"
  private static final String ACCOUNT_NAME = "auto"

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Shared
  DeleteGoogleHttpLoadBalancerAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new DeleteGoogleHttpLoadBalancerAtomicOperationConverter(objectMapper: mapper)
    def accountCredentialsProvider = Mock(AccountCredentialsProvider)
    def mockCredentials = Mock(GoogleNamedAccountCredentials)
    accountCredentialsProvider.getCredentials(_) >> mockCredentials
    converter.accountCredentialsProvider = accountCredentialsProvider
  }

  void "deleteGoogleHttpLoadBalancerDescription type returns DeleteGoogleHttpLoadBalancerDescription and DeleteGoogleHttpLoadBalancerAtomicOperation"() {
    setup:
      def input = [deleteOperationTimeoutSeconds: TIMEOUT_SECONDS,
                   loadBalancerName: HTTP_LOAD_BALANCER_NAME,
                   accountName: ACCOUNT_NAME]

    when:
      def description = converter.convertDescription(input)

    then:
      description instanceof DeleteGoogleHttpLoadBalancerDescription
      description.deleteOperationTimeoutSeconds == TIMEOUT_SECONDS
      description.loadBalancerName == HTTP_LOAD_BALANCER_NAME

    when:
      def operation = converter.convertOperation(input)

    then:
      operation instanceof DeleteGoogleHttpLoadBalancerAtomicOperation
  }
}
