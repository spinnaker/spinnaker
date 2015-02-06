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
import com.netflix.spinnaker.kato.gce.deploy.description.BasicGoogleDeployDescription
import com.netflix.spinnaker.kato.gce.deploy.ops.CopyLastGoogleServerGroupAtomicOperation
import spock.lang.Shared
import spock.lang.Specification

class CopyLastGoogleServerGroupAtomicOperationConverterUnitSpec extends Specification {
  private static final APPLICATION = "spinnaker"
  private static final STACK = "spinnaker-test"
  private static final FREE_FORM_DETAILS = "detail"
  private static final INITIAL_NUM_REPLICAS = 3
  private static final IMAGE = "debian-7-wheezy-v20140415"
  private static final INSTANCE_TYPE = "f1-micro"
  private static final ZONE = "us-central1-b"
  private static final ACCOUNT_NAME = "auto"
  private def source = [zone: "us-central1-b", serverGroupName: "myapp-dev-v000"]

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Shared
  CopyLastGoogleServerGroupAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new CopyLastGoogleServerGroupAtomicOperationConverter(objectMapper: mapper)
    def accountCredentialsProvider = Mock(AccountCredentialsProvider)
    def mockCredentials = Mock(GoogleNamedAccountCredentials)
    accountCredentialsProvider.getCredentials(_) >> mockCredentials
    converter.accountCredentialsProvider = accountCredentialsProvider
  }

  void "copyLastGoogleServerGroupDescription type returns BasicGoogleDeployDescription and CopyLastGoogleServerGroupAtomicOperation"() {
    setup:
      def input = [application: APPLICATION,
                   stack: STACK,
                   initialNumReplicas: INITIAL_NUM_REPLICAS,
                   image: IMAGE,
                   instanceType: INSTANCE_TYPE,
                   zone: ZONE,
                   credentials: ACCOUNT_NAME]

    when:
      def description = converter.convertDescription(input)

    then:
      description instanceof BasicGoogleDeployDescription

    when:
      def operation = converter.convertOperation(input)

    then:
      operation instanceof CopyLastGoogleServerGroupAtomicOperation
  }

  void "copyLastGoogleServerGroupDescription type with source server group returns BasicGoogleDeployDescription and CopyLastGoogleServerGroupAtomicOperation"() {
    setup:
      def input = [application: APPLICATION,
                   stack: STACK,
                   freeFormDetails: FREE_FORM_DETAILS,
                   initialNumReplicas: INITIAL_NUM_REPLICAS,
                   image: IMAGE,
                   instanceType: INSTANCE_TYPE,
                   zone: ZONE,
                   source: source,
                   credentials: ACCOUNT_NAME]

    when:
      def description = converter.convertDescription(input)

    then:
      description instanceof BasicGoogleDeployDescription

    when:
      def operation = converter.convertOperation(input)

    then:
      operation instanceof CopyLastGoogleServerGroupAtomicOperation
  }
}
