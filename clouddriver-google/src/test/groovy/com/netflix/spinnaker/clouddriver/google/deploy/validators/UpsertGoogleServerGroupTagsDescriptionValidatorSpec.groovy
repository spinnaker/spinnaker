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

import com.netflix.spinnaker.clouddriver.google.deploy.description.UpsertGoogleServerGroupTagsDescription
import com.netflix.spinnaker.clouddriver.google.security.GoogleCredentials
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import org.springframework.validation.Errors
import spock.lang.Shared
import spock.lang.Specification

class UpsertGoogleServerGroupTagsDescriptionValidatorSpec extends Specification {
  private static final SERVER_GROUP_NAME = "spinnaker-test-v000"
  private static final REGION = "us-central1"
  private static final ACCOUNT_NAME = "auto"
  private static final TAGS = ["some-tag-1", "some-tag-2"]

  @Shared
  UpsertGoogleServerGroupTagsDescriptionValidator validator

  void setupSpec() {
    validator = new UpsertGoogleServerGroupTagsDescriptionValidator()
    def credentialsRepo = new MapBackedAccountCredentialsRepository()
    def credentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
    def credentials = Mock(GoogleNamedAccountCredentials)
    credentials.getName() >> ACCOUNT_NAME
    credentials.getCredentials() >> new GoogleCredentials(null, null)
    credentialsRepo.save(ACCOUNT_NAME, credentials)
    validator.accountCredentialsProvider = credentialsProvider
  }

  void "pass validation with proper description inputs"() {
    setup:
      def description = new UpsertGoogleServerGroupTagsDescription(serverGroupName: SERVER_GROUP_NAME,
                                                                   region: REGION,
                                                                   tags: TAGS,
                                                                   accountName: ACCOUNT_NAME)
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      0 * errors._
  }

  void "pass validation with empty tag list"() {
    setup:
      def description = new UpsertGoogleServerGroupTagsDescription(serverGroupName: SERVER_GROUP_NAME,
                                                                   region: REGION,
                                                                   tags: [],
                                                                   accountName: ACCOUNT_NAME)
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      0 * errors._
  }

  void "pass validation with null tag list"() {
    setup:
      def description = new UpsertGoogleServerGroupTagsDescription(serverGroupName: SERVER_GROUP_NAME,
                                                                   region: REGION,
                                                                   tags: null,
                                                                   accountName: ACCOUNT_NAME)
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      0 * errors._
  }

  void "tag list containing empty item fails validation"() {
    setup:
      def description = new UpsertGoogleServerGroupTagsDescription(serverGroupName: SERVER_GROUP_NAME,
                                                                   region: REGION,
                                                                   tags: TAGS + "",
                                                                   accountName: ACCOUNT_NAME)
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      1 * errors.rejectValue('tags', "upsertGoogleServerGroupTagsDescription.tag2.empty")
  }

  void "null input fails validation"() {
    setup:
      def description = new UpsertGoogleServerGroupTagsDescription()
      def errors = Mock(Errors)

    when:
      validator.validate([], description, errors)

    then:
      // The point of being explicit here is only to verify the context and property names.
      // We'll assume the policies are covered by the tests on the underlying validator.
      1 * errors.rejectValue('serverGroupName', "upsertGoogleServerGroupTagsDescription.serverGroupName.empty")
      1 * errors.rejectValue('region', "upsertGoogleServerGroupTagsDescription.region.empty")
      1 * errors.rejectValue('credentials', "upsertGoogleServerGroupTagsDescription.credentials.empty")
  }
}
