/*
 * Copyright 2014 Google, Inc.
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
import com.netflix.spinnaker.kato.gce.deploy.description.CreateGoogleInstanceDescription
import com.netflix.spinnaker.kato.gce.deploy.ops.CreateGoogleInstanceAtomicOperation
import spock.lang.Shared
import spock.lang.Specification

class CreateGoogleInstanceAtomicOperationConverterUnitSpec extends Specification {
  private static final INSTANCE_NAME = "my-app-v000"
  private static final IMAGE = "debian-7-wheezy-v20140415"
  private static final INSTANCE_TYPE = "f1-micro"
  private static final ZONE = "us-central1-b"
  private static final ACCOUNT_NAME = "auto"

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Shared
  CreateGoogleInstanceAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new CreateGoogleInstanceAtomicOperationConverter(objectMapper: mapper)
    def accountCredentialsProvider = Mock(AccountCredentialsProvider)
    def mockCredentials = Mock(GoogleNamedAccountCredentials)
    accountCredentialsProvider.getCredentials(_) >> mockCredentials
    converter.accountCredentialsProvider = accountCredentialsProvider
  }

  void "createGoogleInstanceDescription type returns CreateGoogleInstanceDescription and CreateGoogleInstanceAtomicOperation"() {
    setup:
      def input = [instanceName: INSTANCE_NAME,
                   image: IMAGE,
                   instanceType: INSTANCE_TYPE,
                   zone: ZONE,
                   accountName: ACCOUNT_NAME]

    when:
      def description = converter.convertDescription(input)

    then:
      description instanceof CreateGoogleInstanceDescription

    when:
      def operation = converter.convertOperation(input)

    then:
      operation instanceof CreateGoogleInstanceAtomicOperation
  }
}
