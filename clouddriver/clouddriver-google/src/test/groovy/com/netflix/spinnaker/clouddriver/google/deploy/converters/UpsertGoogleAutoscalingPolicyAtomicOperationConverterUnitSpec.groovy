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

package com.netflix.spinnaker.clouddriver.google.deploy.converters

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.google.deploy.description.UpsertGoogleAutoscalingPolicyDescription
import com.netflix.spinnaker.clouddriver.google.deploy.ops.UpsertGoogleAutoscalingPolicyAtomicOperation
import com.netflix.spinnaker.clouddriver.google.model.GoogleAutoscalingPolicy
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.credentials.CredentialsRepository
import spock.lang.Shared
import spock.lang.Specification

class UpsertGoogleAutoscalingPolicyAtomicOperationConverterUnitSpec extends Specification {
  private static final ACCOUNT_NAME = "auto"
  private static final REGION = "us-central1"
  private static final SERVER_GROUP_NAME = "server-group-name"
  private static final GOOGLE_SCALING_POLICY = new GoogleAutoscalingPolicy()

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Shared
  UpsertGoogleAutoscalingPolicyAtomicOperationConverter converter

  def setupSpec() {
    this.converter = new UpsertGoogleAutoscalingPolicyAtomicOperationConverter()
    def credentialsRepository = Mock(CredentialsRepository)
    def mockCredentials = Mock(GoogleNamedAccountCredentials)
    credentialsRepository.getOne(_) >> mockCredentials
    converter.credentialsRepository = credentialsRepository
    converter.objectMapper = mapper
  }

  void "upsertGoogleScalingPolicyDescription type returns UpsertGoogleScalingPolicyDescription and UpsertGoogleScalingPolicyAtomicOperation"() {
    setup:
    def input = [
      serverGroupName  : SERVER_GROUP_NAME,
      region           : REGION,
      credentials      : ACCOUNT_NAME,
      autoscalingPolicy: GOOGLE_SCALING_POLICY
    ]

    when:
    def description = converter.convertDescription(input)

    then:
    description instanceof UpsertGoogleAutoscalingPolicyDescription

    when:
    def operation = converter.convertOperation(input)

    then:
    operation instanceof UpsertGoogleAutoscalingPolicyAtomicOperation
  }
}
