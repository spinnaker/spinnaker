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
import com.netflix.spinnaker.clouddriver.google.deploy.description.DeleteGoogleSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.google.deploy.ops.DeleteGoogleSecurityGroupAtomicOperation
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Shared
import spock.lang.Specification

class DeleteGoogleSecurityGroupAtomicOperationConverterUnitSpec extends Specification {
  private static final SECURITY_GROUP_NAME = "spinnaker-test-sg"
  private static final ACCOUNT_NAME = "some-account-name"

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Shared
  DeleteGoogleSecurityGroupAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new DeleteGoogleSecurityGroupAtomicOperationConverter(objectMapper: mapper)
    def accountCredentialsProvider = Mock(AccountCredentialsProvider)
    def mockCredentials = Mock(GoogleNamedAccountCredentials)
    accountCredentialsProvider.getCredentials(_) >> mockCredentials
    converter.accountCredentialsProvider = accountCredentialsProvider
  }

  void "deleteGoogleSecurityGroupDescription type returns DeleteGoogleSecurityGroupDescription and DeleteGoogleSecurityGroupAtomicOperation"() {
    setup:
      def input = [securityGroupName: SECURITY_GROUP_NAME,
                   accountName: ACCOUNT_NAME]

    when:
      def description = converter.convertDescription(input)

    then:
      description instanceof DeleteGoogleSecurityGroupDescription

    when:
      def operation = converter.convertOperation(input)

    then:
      operation instanceof DeleteGoogleSecurityGroupAtomicOperation
  }
}
