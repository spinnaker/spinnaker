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

package com.netflix.spinnaker.clouddriver.appengine.deploy.validators

import com.netflix.spinnaker.clouddriver.appengine.AppengineOperation
import com.netflix.spinnaker.clouddriver.appengine.deploy.description.UpsertAppengineAutoscalingPolicyDescription
import com.netflix.spinnaker.clouddriver.appengine.provider.view.AppengineClusterProvider
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@AppengineOperation(AtomicOperations.UPSERT_SCALING_POLICY)
@Component
class UpsertAppengineAutoscalingPolicyDescriptionValidator extends DescriptionValidator<UpsertAppengineAutoscalingPolicyDescription> {
  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  AppengineClusterProvider appengineClusterProvider

  @Override
  void validate(List priorDescriptions, UpsertAppengineAutoscalingPolicyDescription description, ValidationErrors errors) {
    def helper = new StandardAppengineAttributeValidator("upsertAppengineAutoscalingPolicyAtomicOperationDescription", errors)

    if (!helper.validateCredentials(description.accountName, accountCredentialsProvider)) {
      return
    }

    helper.validateNotEmpty(description.serverGroupName, "serverGroupName")
    helper.validateNotEmpty(description.minIdleInstances, "minIdleInstances")
    helper.validateNotEmpty(description.maxIdleInstances, "maxIdleInstances")

    helper.validateMaxNotLessThanMin(
      description.minIdleInstances,
      description.maxIdleInstances,
      "minIdleInstances",
      "maxIdleInstances")
    helper.validateNonNegative(description.minIdleInstances, "minIdleInstances")
    helper.validateNonNegative(description.maxIdleInstances, "maxIdleInstances")

    helper.validateServerGroupSupportsAutoscalingPolicyUpsert(
      description.serverGroupName,
      description.credentials,
      appengineClusterProvider,
      "serverGroupName")
  }
}
