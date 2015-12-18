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
import com.netflix.spinnaker.clouddriver.google.deploy.description.UpsertGoogleSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.google.deploy.ops.UpsertGoogleSecurityGroupAtomicOperation
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Shared
import spock.lang.Specification

class UpsertGoogleSecurityGroupAtomicOperationConverterUnitSpec extends Specification {
  private static final SECURITY_GROUP_NAME = "spinnaker-security-group-1"
  private static final NETWORK_NAME = "default"
  private static final SOURCE_RANGE = "192.0.0.0/8"
  private static final SOURCE_TAG = "some-source-tag"
  private static final IP_PROTOCOL = "tcp"
  private static final PORT_RANGE = "8070-8080"
  private static final TARGET_TAG = "some-target-tag"
  private static final ACCOUNT_NAME = "auto"

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Shared
  UpsertGoogleSecurityGroupAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new UpsertGoogleSecurityGroupAtomicOperationConverter(objectMapper: mapper)
    def accountCredentialsProvider = Mock(AccountCredentialsProvider)
    def mockCredentials = Mock(GoogleNamedAccountCredentials)
    accountCredentialsProvider.getCredentials(_) >> mockCredentials
    converter.accountCredentialsProvider = accountCredentialsProvider
  }

  void "upsertGoogleSecurityGroupDescription type returns UpsertGoogleSecurityGroupDescription and UpsertGoogleSecurityGroupAtomicOperation"() {
    setup:
      def input = [
          securityGroupName: SECURITY_GROUP_NAME,
          network: NETWORK_NAME,
          sourceRanges: [SOURCE_RANGE],
          sourceTags: [SOURCE_TAG],
          allowed: [
              [
                  ipProtocol: IP_PROTOCOL,
                  portRanges: [PORT_RANGE]
              ]
          ],
          targetTags: [TARGET_TAG],
          accountName: ACCOUNT_NAME
      ]

    when:
      def description = converter.convertDescription(input)

    then:
      description instanceof UpsertGoogleSecurityGroupDescription
      description.securityGroupName == SECURITY_GROUP_NAME
      description.network == NETWORK_NAME
      description.sourceRanges == [SOURCE_RANGE]
      description.sourceTags == [SOURCE_TAG]
      description.allowed.size() == 1
      description.allowed[0].ipProtocol == IP_PROTOCOL
      description.allowed[0].portRanges == [PORT_RANGE]
      description.targetTags == [TARGET_TAG]

    when:
      def operation = converter.convertOperation(input)

    then:
      operation instanceof UpsertGoogleSecurityGroupAtomicOperation
  }
}
