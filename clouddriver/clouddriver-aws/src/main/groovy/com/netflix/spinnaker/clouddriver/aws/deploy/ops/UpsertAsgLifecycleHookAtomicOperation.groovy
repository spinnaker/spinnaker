/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops

import com.amazonaws.services.autoscaling.model.PutLifecycleHookRequest
import com.netflix.spinnaker.clouddriver.aws.deploy.asg.AsgLifecycleHookWorker
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAsgLifecycleHookDescription
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.services.IdGenerator
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class UpsertAsgLifecycleHookAtomicOperation implements AtomicOperation<Void> {

  private static final String BASE_PHASE = "UPSERT_ASG_LIFECYCLE"

  @Autowired
  AmazonClientProvider amazonClientProvider

  IdGenerator idGenerator = new IdGenerator()

  UpsertAsgLifecycleHookDescription description

  UpsertAsgLifecycleHookAtomicOperation(UpsertAsgLifecycleHookDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    final lifecycleHookName = description.name ?: "${description.serverGroupName}-lifecycle-${idGenerator.nextId()}"
    final request = new PutLifecycleHookRequest(
      lifecycleHookName: AsgLifecycleHookWorker.cleanLifecycleHookName(lifecycleHookName),
      autoScalingGroupName: description.serverGroupName,
      lifecycleTransition: description.lifecycleTransition.toString(),
      roleARN: description.roleARN,
      notificationTargetARN: description.notificationTargetARN,
      notificationMetadata: description.notificationMetadata,
      heartbeatTimeout: description.heartbeatTimeout,
      defaultResult: description.defaultResult.toString()
    )

    final autoScaling = amazonClientProvider.getAutoScaling(description.credentials, description.region, true)
    autoScaling.putLifecycleHook(request)

    return null
  }
}
