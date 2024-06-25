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

package com.netflix.spinnaker.clouddriver.google.deploy.converters

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.services.compute.model.AutoscalingPolicy
import com.netflix.spinnaker.clouddriver.google.deploy.description.ResizeGoogleServerGroupDescription
import com.netflix.spinnaker.clouddriver.google.deploy.description.UpsertGoogleAutoscalingPolicyDescription
import com.netflix.spinnaker.clouddriver.google.deploy.ops.ResizeGoogleServerGroupAtomicOperation
import com.netflix.spinnaker.clouddriver.google.deploy.ops.UpsertGoogleAutoscalingPolicyAtomicOperation
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.credentials.CredentialsRepository
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class ResizeGoogleServerGroupAtomicOperationConverterUnitSpec extends Specification {
  private static final SERVER_GROUP_NAME = "spinnaker-test-v000"
  private static final TARGET_SIZE = 5
  private static final REGION = "us-central1"
  private static final ZONE = "us-central1-b"
  private static final ACCOUNT_NAME = "auto"

  @Shared
  ObjectMapper mapper = new ObjectMapper()

  @Unroll
  void "#descriptionType and #operationType are returned when autoscalingPolicy is #autoscalingPolicy"() {
    setup:
      def input = [serverGroupName: SERVER_GROUP_NAME,
                   targetSize: TARGET_SIZE,
                   region: REGION,
                   zone: ZONE,
                   accountName: ACCOUNT_NAME]
      GoogleClusterProvider googleClusterProviderMock = Mock(GoogleClusterProvider)
      ResizeGoogleServerGroupAtomicOperationConverter converter =
        new ResizeGoogleServerGroupAtomicOperationConverter(googleClusterProvider: googleClusterProviderMock, objectMapper: mapper)
      def credentialsRepository = Mock(CredentialsRepository)
      def mockCredentials = Mock(GoogleNamedAccountCredentials)
      credentialsRepository.getOne(_) >> mockCredentials
      converter.credentialsRepository = credentialsRepository

    when:
      def description = converter.convertDescription(input)

    then:
      1 * googleClusterProviderMock.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME) >>
        new GoogleServerGroup(autoscalingPolicy: autoscalingPolicy).view
      description in descriptionType

    when:
      def operation = converter.convertOperation(input)

    then:
      1 * googleClusterProviderMock.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME) >>
        new GoogleServerGroup(autoscalingPolicy: autoscalingPolicy).view
      operation in operationType

    where:
      autoscalingPolicy                            || descriptionType                          | operationType
      null                                         || ResizeGoogleServerGroupDescription       | ResizeGoogleServerGroupAtomicOperation
      new AutoscalingPolicy(coolDownPeriodSec: 45) || UpsertGoogleAutoscalingPolicyDescription | UpsertGoogleAutoscalingPolicyAtomicOperation
  }

  void "should convert target size to ints"() {
    setup:
      def input = [application: "app", targetSize: desired, region: REGION]
      GoogleClusterProvider googleClusterProviderMock = Mock(GoogleClusterProvider)
      ResizeGoogleServerGroupAtomicOperationConverter converter =
        new ResizeGoogleServerGroupAtomicOperationConverter(googleClusterProvider: googleClusterProviderMock, objectMapper: mapper)
      def credentialsRepository = Mock(CredentialsRepository)
      def mockCredentials = Mock(GoogleNamedAccountCredentials)
      credentialsRepository.getOne(_) >> mockCredentials
      converter.credentialsRepository = credentialsRepository

    when:
      def description = converter.convertDescription(input)

    then:
      1 * googleClusterProviderMock.getServerGroup(_, REGION, _) >> new GoogleServerGroup().view
      description.targetSize == desired as int

    where:
      desired = "4"
  }
}
