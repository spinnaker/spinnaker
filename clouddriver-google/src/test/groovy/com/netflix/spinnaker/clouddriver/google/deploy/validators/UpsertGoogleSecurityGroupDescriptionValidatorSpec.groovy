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

package com.netflix.spinnaker.clouddriver.google.deploy.validators

import com.netflix.spinnaker.clouddriver.google.deploy.description.UpsertGoogleSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification

class UpsertGoogleSecurityGroupDescriptionValidatorSpec extends Specification {
  private static final SECURITY_GROUP_NAME = "spinnaker-sg-1"
  private static final DESCRIPTION = "Some firewall description..."
  private static final NETWORK_NAME = "default"
  private static final SOURCE_RANGE = "192.0.0.0/8"
  private static final SOURCE_TAG = "some-source-tag"
  private static final IP_PROTOCOL = "tcp"
  private static final PORT_RANGE = "8070-8080"
  private static final TARGET_TAG = "some-target-tag"
  private static final ACCOUNT_NAME = "auto"

  @Shared
  UpsertGoogleSecurityGroupDescriptionValidator validator

  void setupSpec() {
    validator = new UpsertGoogleSecurityGroupDescriptionValidator()
    def credentialsRepo = new MapBackedAccountCredentialsRepository()
    def credentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
    def credentials = new GoogleNamedAccountCredentials.Builder().name(ACCOUNT_NAME).regionLookupEnabled(false).build()
    credentialsRepo.save(ACCOUNT_NAME, credentials)
    validator.accountCredentialsProvider = credentialsProvider
  }

  void "pass validation with proper description inputs"() {
    setup:
      def description = new UpsertGoogleSecurityGroupDescription(
          securityGroupName: SECURITY_GROUP_NAME,
          description: DESCRIPTION,
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
      )
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      0 * errors._
  }

  void "pass validation without description, source ranges, source tags, allowed list and without target tags"() {
    setup:
      def description = new UpsertGoogleSecurityGroupDescription(
          securityGroupName: SECURITY_GROUP_NAME,
          network: NETWORK_NAME,
          accountName: ACCOUNT_NAME
      )
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      0 * errors._
  }

  void "null input fails validation"() {
    setup:
      def description = new UpsertGoogleSecurityGroupDescription(network: "")
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      1 * errors.rejectValue('credentials', _)
      1 * errors.rejectValue('securityGroupName', _)
      1 * errors.rejectValue('network', _)
  }
}
