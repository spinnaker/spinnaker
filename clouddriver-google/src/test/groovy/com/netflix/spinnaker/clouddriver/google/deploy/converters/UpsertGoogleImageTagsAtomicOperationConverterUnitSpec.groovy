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

package com.netflix.spinnaker.clouddriver.google.deploy.converters

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.google.deploy.description.UpsertGoogleImageTagsDescription
import com.netflix.spinnaker.clouddriver.google.deploy.ops.UpsertGoogleImageTagsAtomicOperation
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Shared
import spock.lang.Specification

class UpsertGoogleImageTagsAtomicOperationConverterUnitSpec extends Specification {
  private static final IMAGE_NAME = 'spinnaker-image-v000'
  private static final TAGS = ['some-key-1': 'some-key-2']
  private static final ACCOUNT_NAME = 'auto'

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Shared
  UpsertGoogleImageTagsAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new UpsertGoogleImageTagsAtomicOperationConverter(objectMapper: mapper)
    def accountCredentialsProvider = Mock(AccountCredentialsProvider)
    def mockCredentials = Mock(GoogleNamedAccountCredentials)
    accountCredentialsProvider.getCredentials(_) >> mockCredentials
    converter.accountCredentialsProvider = accountCredentialsProvider
  }

  void "upsertGoogleImageTagsDescription type returns UpsertGoogleImageTagsDescription and UpsertGoogleImageTagsAtomicOperation"() {
    setup:
      def input = [
        imageName: IMAGE_NAME,
        tags: TAGS,
        accountName: ACCOUNT_NAME
      ]

    when:
      def description = converter.convertDescription(input)
    then:
      description instanceof UpsertGoogleImageTagsDescription

    when:
      def operation = converter.convertOperation(input)
    then:
      operation instanceof UpsertGoogleImageTagsAtomicOperation
  }
}
