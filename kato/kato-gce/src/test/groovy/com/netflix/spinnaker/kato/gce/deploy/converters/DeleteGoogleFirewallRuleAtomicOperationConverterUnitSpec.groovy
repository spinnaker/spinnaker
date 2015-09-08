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
import com.netflix.spinnaker.kato.gce.deploy.description.DeleteGoogleFirewallRuleDescription
import com.netflix.spinnaker.kato.gce.deploy.ops.DeleteGoogleFirewallRuleAtomicOperation
import spock.lang.Shared
import spock.lang.Specification

class DeleteGoogleFirewallRuleAtomicOperationConverterUnitSpec extends Specification {
  private static final FIREWALL_RULE_NAME = "spinnaker-test-sg"
  private static final ACCOUNT_NAME = "some-account-name"

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Shared
  DeleteGoogleFirewallRuleAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new DeleteGoogleFirewallRuleAtomicOperationConverter(objectMapper: mapper)
    def accountCredentialsProvider = Mock(AccountCredentialsProvider)
    def mockCredentials = Mock(GoogleNamedAccountCredentials)
    accountCredentialsProvider.getCredentials(_) >> mockCredentials
    converter.accountCredentialsProvider = accountCredentialsProvider
  }

  void "deleteGoogleFirewallRuleDescription type returns DeleteGoogleFirewallRuleDescription and DeleteGoogleFirewallRuleAtomicOperation"() {
    setup:
      def input = [firewallRuleName: FIREWALL_RULE_NAME,
                   accountName: ACCOUNT_NAME]

    when:
      def description = converter.convertDescription(input)

    then:
      description instanceof DeleteGoogleFirewallRuleDescription

    when:
      def operation = converter.convertOperation(input)

    then:
      operation instanceof DeleteGoogleFirewallRuleAtomicOperation
  }
}
