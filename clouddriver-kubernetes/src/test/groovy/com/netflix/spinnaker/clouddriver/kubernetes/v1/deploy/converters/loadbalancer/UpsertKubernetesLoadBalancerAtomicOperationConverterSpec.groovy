/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.converters.loadbalancer

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.loadbalancer.KubernetesLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.ops.loadbalancer.UpsertKubernetesLoadBalancerAtomicOperation
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Shared
import spock.lang.Specification

class UpsertKubernetesLoadBalancerAtomicOperationConverterSpec extends Specification {
  private static final ACCOUNT = "my-test-account"
  private static final NAME = "johanson"

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Shared
  UpsertKubernetesLoadBalancerAtomicOperationConverter converter

  def mockCredentials

  def setupSpec() {
    converter = new UpsertKubernetesLoadBalancerAtomicOperationConverter(objectMapper: mapper)
  }

  def setup() {
    mockCredentials = Mock(KubernetesNamedAccountCredentials)
    converter.accountCredentialsProvider = Mock(AccountCredentialsProvider)
  }

  void "UpsertKubernetesLoadBalancerAtomicOperationSpec matches type signature of parent method"() {
    setup:
      def input = [name: NAME,
                   account: ACCOUNT]
    when:
      def description = converter.convertDescription(input)

    then:
      1 * converter.accountCredentialsProvider.getCredentials(_) >> mockCredentials
      description instanceof KubernetesLoadBalancerDescription

    when:
      def operation = converter.convertOperation(input)

    then:
      1 * converter.accountCredentialsProvider.getCredentials(_) >> mockCredentials
      operation instanceof UpsertKubernetesLoadBalancerAtomicOperation
  }
}
