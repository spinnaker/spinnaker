/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.appengine.deploy.converters

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.appengine.deploy.description.EnableDisableAppEngineDescription
import com.netflix.spinnaker.clouddriver.appengine.deploy.ops.EnableAppEngineAtomicOperation
import com.netflix.spinnaker.clouddriver.appengine.security.AppEngineNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import spock.lang.Shared
import spock.lang.Specification

class EnableAppEngineAtomicOperationConverterSpec extends Specification {
  private static final ACCOUNT_NAME = "my-appengine-account"
  private static final SERVER_GROUP_NAME = 'app-stack-detail-v000'

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Shared
  EnableAppEngineAtomicOperationConverter converter

  def setupSpec() {
    converter = new EnableAppEngineAtomicOperationConverter(objectMapper: mapper)
    def accountCredentialsProvider = Mock(AccountCredentialsProvider)
    def mockCredentials = Mock(AppEngineNamedAccountCredentials)
    accountCredentialsProvider.getCredentials(_) >> mockCredentials
    converter.accountCredentialsProvider = accountCredentialsProvider
  }

  void "enableAppEngineDescription type returns EnableDisableAppEngineDescription and EnableAppEngineAtomicOperation"() {
    setup:
      def input = [ credentials: ACCOUNT_NAME, serverGroupName: SERVER_GROUP_NAME ]

    when:
      def description = converter.convertDescription(input)

    then:
      description instanceof EnableDisableAppEngineDescription

    when:
      def operation = converter.convertOperation(input)

    then:
      operation instanceof EnableAppEngineAtomicOperation
  }
}
