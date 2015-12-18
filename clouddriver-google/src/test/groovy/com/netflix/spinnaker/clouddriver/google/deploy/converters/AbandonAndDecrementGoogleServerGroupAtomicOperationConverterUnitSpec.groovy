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
import com.netflix.spinnaker.clouddriver.google.deploy.description.AbandonAndDecrementGoogleServerGroupDescription
import com.netflix.spinnaker.clouddriver.google.deploy.ops.AbandonAndDecrementGoogleServerGroupAtomicOperation
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Shared
import spock.lang.Specification

class AbandonAndDecrementGoogleServerGroupAtomicOperationConverterUnitSpec extends Specification {
  private static final ACCOUNT_NAME = "auto"
  private static final ZONE = "us-central1-b"
  private static final SERVER_GROUP_NAME = "my-server-group-name"

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Shared
  AbandonAndDecrementGoogleServerGroupAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new AbandonAndDecrementGoogleServerGroupAtomicOperationConverter(objectMapper: mapper)
    def accountCredentialsProvider = Mock(AccountCredentialsProvider)
    def mockCredentials = Mock(GoogleNamedAccountCredentials)
    accountCredentialsProvider.getCredentials(_) >> mockCredentials
    converter.accountCredentialsProvider = accountCredentialsProvider
  }

  void "abandonAndDecrementGoogleServerGroupDescription type returns AbandonAndDecrementGoogleServerGroupDescription and AbandonAndDecrementGoogleServerGroupAtomicOperation"() {
    setup:
      def input = [serverGroupName: SERVER_GROUP_NAME, zone: ZONE, credentials: ACCOUNT_NAME]

    when:
      def description = converter.convertDescription(input)

    then:
      description instanceof AbandonAndDecrementGoogleServerGroupDescription

    when:
      def operation = converter.convertOperation(input)

    then:
      operation instanceof AbandonAndDecrementGoogleServerGroupAtomicOperation
  }
}
