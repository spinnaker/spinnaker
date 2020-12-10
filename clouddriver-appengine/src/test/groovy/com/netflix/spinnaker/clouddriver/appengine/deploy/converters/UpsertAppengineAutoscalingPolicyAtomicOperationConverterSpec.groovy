/*
 * Copyright 2017 Google, Inc.
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

import com.netflix.spinnaker.clouddriver.appengine.deploy.description.UpsertAppengineAutoscalingPolicyDescription
import com.netflix.spinnaker.clouddriver.appengine.deploy.ops.UpsertAppengineAutoscalingPolicyAtomicOperation
import com.netflix.spinnaker.clouddriver.appengine.security.AppengineNamedAccountCredentials
import com.netflix.spinnaker.credentials.CredentialsRepository
import spock.lang.Shared
import spock.lang.Specification

class UpsertAppengineAutoscalingPolicyAtomicOperationConverterSpec extends Specification {
  private static final ACCOUNT_NAME = "my-appengine-account"
  private static final SERVER_GROUP_NAME = "app-stack-detail-v000"
  private static final MIN_IDLE_INSTANCES = 10
  private static final MAX_IDLE_INSTANCES = 20

  @Shared
  UpsertAppengineAutoscalingPolicyAtomicOperationConverter converter

  def setupSpec() {
    converter = new UpsertAppengineAutoscalingPolicyAtomicOperationConverter()
    def credentialsRepository = Mock(CredentialsRepository)
    def mockCredentials = Mock(AppengineNamedAccountCredentials)
    credentialsRepository.getOne(_) >> mockCredentials
    converter.credentialsRepository = credentialsRepository
  }

  void "upsertAppengineAutoscalingPolicyDescription type returns UpsertAppengineAutoscalingPolicyDescription and UpsertAppengineAutoscalingPolicyAtomicOperation"() {
    setup:
    def input = [
      credentials: ACCOUNT_NAME,
      loadBalancerName: SERVER_GROUP_NAME,
      minIdleInstances: MIN_IDLE_INSTANCES,
      maxIdleInstances: MAX_IDLE_INSTANCES,
    ]

    when:
    def description = converter.convertDescription(input)

    then:
    description instanceof UpsertAppengineAutoscalingPolicyDescription

    when:
    def operation = converter.convertOperation(input)

    then:
    operation instanceof UpsertAppengineAutoscalingPolicyAtomicOperation
  }
}
