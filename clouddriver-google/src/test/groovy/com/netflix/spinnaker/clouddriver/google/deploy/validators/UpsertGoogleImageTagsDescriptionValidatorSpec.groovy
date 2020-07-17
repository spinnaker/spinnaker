/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.deploy.validators

import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import com.netflix.spinnaker.clouddriver.google.deploy.description.UpsertGoogleImageTagsDescription
import com.netflix.spinnaker.clouddriver.google.security.FakeGoogleCredentials
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository
import spock.lang.Shared
import spock.lang.Specification

class UpsertGoogleImageTagsDescriptionValidatorSpec extends Specification {
  private static final IMAGE_NAME = 'spinnaker-image-v000'
  private static final TAGS = ['some-key-1': 'some-val-2']
  private static final ACCOUNT_NAME = 'auto'

  @Shared
  UpsertGoogleImageTagsDescriptionValidator validator

  void setupSpec() {
    validator = new UpsertGoogleImageTagsDescriptionValidator()
    def credentialsRepo = new MapBackedAccountCredentialsRepository()
    def credentialsProvider = new DefaultAccountCredentialsProvider(credentialsRepo)
    def credentials = new GoogleNamedAccountCredentials.Builder().name(ACCOUNT_NAME).credentials(new FakeGoogleCredentials()).build()
    credentialsRepo.save(ACCOUNT_NAME, credentials)
    validator.accountCredentialsProvider = credentialsProvider
  }

  void "pass validation with proper description inputs"() {
    setup:
      def description = new UpsertGoogleImageTagsDescription(imageName: IMAGE_NAME,
                                                             tags: TAGS,
                                                             accountName: ACCOUNT_NAME)
      def errors = Mock(ValidationErrors)

    when:
      validator.validate([], description, errors)

    then:
      0 * errors._
  }

  void "pass validation with empty tag map"() {
    setup:
      def description = new UpsertGoogleImageTagsDescription(imageName: IMAGE_NAME,
                                                             tags: [:],
                                                             accountName: ACCOUNT_NAME)
      def errors = Mock(ValidationErrors)

    when:
      validator.validate([], description, errors)

    then:
      0 * errors._
  }

  void "pass validation with empty tag value"() {
    setup:
      def description = new UpsertGoogleImageTagsDescription(imageName: IMAGE_NAME,
                                                             tags: TAGS + ['some-key-2': ''],
                                                             accountName: ACCOUNT_NAME)
      def errors = Mock(ValidationErrors)

    when:
      validator.validate([], description, errors)

    then:
      0 * errors._
  }

  void "null tag map fails validation"() {
    setup:
      def description = new UpsertGoogleImageTagsDescription(imageName: IMAGE_NAME,
                                                             accountName: ACCOUNT_NAME)
      def errors = Mock(ValidationErrors)

    when:
      validator.validate([], description, errors)

    then:
     1 * errors.rejectValue('tags', 'upsertGoogleImageTagsDescription.tags.empty')
  }

  void "tag map containing empty key fails validation"() {
    setup:
      def description = new UpsertGoogleImageTagsDescription(imageName: IMAGE_NAME,
                                                             tags: TAGS + ['': 'some-val-2'],
                                                             accountName: ACCOUNT_NAME)
      def errors = Mock(ValidationErrors)

    when:
      validator.validate([], description, errors)

    then:
      1 * errors.rejectValue('tags', 'upsertGoogleImageTagsDescription.tags.key.empty')
  }

  void "null input fails validation"() {
    setup:
      def description = new UpsertGoogleImageTagsDescription()
      def errors = Mock(ValidationErrors)

    when:
      validator.validate([], description, errors)

    then:
      // The point of being explicit here is only to verify the context and property names.
      // We'll assume the policies are covered by the tests on the underlying validator.
      1 * errors.rejectValue('imageName', 'upsertGoogleImageTagsDescription.imageName.empty')
      1 * errors.rejectValue('tags', 'upsertGoogleImageTagsDescription.tags.empty')
      1 * errors.rejectValue('credentials', 'upsertGoogleImageTagsDescription.credentials.empty')
  }
}
