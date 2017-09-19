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

package com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.converters.servergroup

import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.description.servergroup.DeployKubernetesAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.ops.servergroup.DeployKubernetesAtomicOperation
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.fasterxml.jackson.databind.ObjectMapper
import spock.lang.Shared
import spock.lang.Specification

class DeployKubernetesAtomicOperationConverterSpec extends Specification {
  private static final ACCOUNT = "my-test-account"
  private static final APPLICATION = "app"
  private static final STACK = "stack"
  private static final DETAILS = "details"

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Shared
  DeployKubernetesAtomicOperationConverter converter

  def mockCredentials

  def setupSpec() {
    converter = new DeployKubernetesAtomicOperationConverter(objectMapper: mapper)
  }

  def setup() {
    mockCredentials = Mock(KubernetesNamedAccountCredentials)
    converter.accountCredentialsProvider = Mock(AccountCredentialsProvider)
  }

  void "DeployKubernetesAtomicOperationConverter type returns DeployKubernetesAtomicOperation and DeployKubernetesAtomicOperationDescription"() {
    setup:
      def input = [app: APPLICATION,
                   stack: STACK,
                   freeFormDetails: DETAILS,
                   account: ACCOUNT]
    when:
      def description = converter.convertDescription(input)

    then:
      1 * converter.accountCredentialsProvider.getCredentials(_) >> mockCredentials
      description instanceof DeployKubernetesAtomicOperationDescription

    when:
      def operation = converter.convertOperation(input)

    then:
      1 * converter.accountCredentialsProvider.getCredentials(_) >> mockCredentials
      operation instanceof DeployKubernetesAtomicOperation
  }
}
