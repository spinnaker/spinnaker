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
import com.netflix.spinnaker.kato.gce.deploy.description.UpsertGoogleServerGroupTagsDescription
import com.netflix.spinnaker.kato.gce.deploy.ops.UpsertGoogleServerGroupTagsAtomicOperation
import spock.lang.Shared
import spock.lang.Specification

class UpsertGoogleServerGroupTagsAtomicOperationConverterUnitSpec extends Specification {
  private static final REPLICA_POOL_NAME = "spinnaker-test-v000"
  private static final ZONE = "us-central1-b"
  private static final ACCOUNT_NAME = "auto"
  private static final TAGS = ["some-tag-1", "some-tag-2"]

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Shared
  UpsertGoogleServerGroupTagsAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new UpsertGoogleServerGroupTagsAtomicOperationConverter(objectMapper: mapper)
    def accountCredentialsProvider = Mock(AccountCredentialsProvider)
    def mockCredentials = Mock(GoogleNamedAccountCredentials)
    accountCredentialsProvider.getCredentials(_) >> mockCredentials
    converter.accountCredentialsProvider = accountCredentialsProvider
  }

  void "upsertGoogleServerGroupTagsDescription type returns UpsertGoogleServerGroupTagsDescription and UpsertGoogleServerGroupTagsAtomicOperation"() {
    setup:
      def input = [
        replicaPoolName: REPLICA_POOL_NAME,
        zone: ZONE,
        tags: TAGS,
        accountName: ACCOUNT_NAME
      ]

    when:
      def description = converter.convertDescription(input)
    then:
      description instanceof UpsertGoogleServerGroupTagsDescription

    when:
      def operation = converter.convertOperation(input)
    then:
      operation instanceof UpsertGoogleServerGroupTagsAtomicOperation
  }
}
