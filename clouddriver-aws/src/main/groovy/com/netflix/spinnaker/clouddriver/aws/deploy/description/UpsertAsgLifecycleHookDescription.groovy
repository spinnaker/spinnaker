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

package com.netflix.spinnaker.clouddriver.aws.deploy.description

import com.netflix.spinnaker.clouddriver.aws.model.AmazonAsgLifecycleHook.DefaultResult
import com.netflix.spinnaker.clouddriver.aws.model.AmazonAsgLifecycleHook.Transition
import com.netflix.spinnaker.clouddriver.security.resources.ServerGroupNameable

class UpsertAsgLifecycleHookDescription extends AbstractAmazonCredentialsDescription implements ServerGroupNameable {

  // required
  String serverGroupName
  String region
  String roleARN
  String notificationTargetARN

  // optional
  String name
  Transition lifecycleTransition = Transition.EC2InstanceTerminating
  String notificationMetadata
  Integer heartbeatTimeout = 3600
  DefaultResult defaultResult = DefaultResult.ABANDON

  @Override
  Collection<String> getServerGroupNames() {
    return [serverGroupName]
  }
}

