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

package com.netflix.spinnaker.clouddriver.google.deploy.converters

import com.google.api.services.compute.model.AutoscalingPolicy
import com.netflix.spinnaker.clouddriver.google.GoogleOperation
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.deploy.description.ResizeGoogleServerGroupDescription
import com.netflix.spinnaker.clouddriver.google.deploy.description.UpsertGoogleAutoscalingPolicyDescription
import com.netflix.spinnaker.clouddriver.google.deploy.ops.ResizeGoogleServerGroupAtomicOperation
import com.netflix.spinnaker.clouddriver.google.deploy.ops.UpsertGoogleAutoscalingPolicyAtomicOperation
import com.netflix.spinnaker.clouddriver.google.model.GoogleAutoscalingPolicy
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@GoogleOperation(AtomicOperations.RESIZE_SERVER_GROUP)
@Component("resizeGoogleServerGroupDescription")
class ResizeGoogleServerGroupAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  @Autowired
  GoogleClusterProvider googleClusterProvider

  @Override
  AtomicOperation convertOperation(Map input) {
    // If the target server group has an Autoscaler configured we need to modify that policy as opposed to the
    // target size of the managed instance group itself.
    AutoscalingPolicy autoscalingPolicy = resolveServerGroup(input)?.autoscalingPolicy
    def convertedDescription = convertDescription(input, autoscalingPolicy)

    if (autoscalingPolicy) {
      new UpsertGoogleAutoscalingPolicyAtomicOperation(convertedDescription)
    } else {
      new ResizeGoogleServerGroupAtomicOperation(convertedDescription)
    }
  }

  def convertDescription(Map input, AutoscalingPolicy autoscalingPolicy) {
    if (autoscalingPolicy) {
      UpsertGoogleAutoscalingPolicyDescription upsertGoogleAutoscalingPolicyDescription =
        GoogleAtomicOperationConverterHelper.convertDescription(input, this, UpsertGoogleAutoscalingPolicyDescription)

      // Retrieve the existing autoscaling policy and overwrite the min/max settings.
      GoogleAutoscalingPolicy googleAutoscalingPolicy =
        GCEUtil.buildAutoscalingPolicyDescriptionFromAutoscalingPolicy(autoscalingPolicy)

      upsertGoogleAutoscalingPolicyDescription.autoscalingPolicy = googleAutoscalingPolicy
      upsertGoogleAutoscalingPolicyDescription.autoscalingPolicy.minNumReplicas = input.capacity?.min
      upsertGoogleAutoscalingPolicyDescription.autoscalingPolicy.maxNumReplicas = input.capacity?.max
      if (input?.writeMetadata != null) {
        upsertGoogleAutoscalingPolicyDescription.writeMetadata = input?.writeMetadata
      }

      // Override autoscaling mode. This is useful in situations where we need the resize to happen
      // regardless of previous autoscaling mode (e.g. scale down in red/black deployment strategies).
      if (input?.autoscalingMode) {
        upsertGoogleAutoscalingPolicyDescription.autoscalingPolicy.mode = input.autoscalingMode
      }

      return upsertGoogleAutoscalingPolicyDescription
    } else {
      return GoogleAtomicOperationConverterHelper.convertDescription(input, this, ResizeGoogleServerGroupDescription)
    }
  }

  @Override
  def convertDescription(Map input) {
    return convertDescription(input, resolveServerGroup(input)?.autoscalingPolicy)
  }

  private GoogleServerGroup.View resolveServerGroup(Map input) {
    def accountName = input.accountName ?: input.credentials
    def region = input.region
    def serverGroupName = input.serverGroupName

    return GCEUtil.queryServerGroup(googleClusterProvider, accountName, region, serverGroupName)
  }
}
