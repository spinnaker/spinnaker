/*
 * Copyright 2014 Netflix, Inc.
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

import com.netflix.spinnaker.clouddriver.deploy.description.EnableDisableDescriptionTrait
import com.netflix.spinnaker.clouddriver.security.resources.ServerGroupsNameable

/**
 * Description for "enabling" a supplied ASG. "Enabling" means Resuming "AddToLoadBalancer", "Launch", and "Terminate" processes on an ASG. If Eureka/Discovery is available, setting a status
 * override will also be achieved.
 *
 * Description for "disabling" a supplied ASG. "Disabling" means Suspending "AddToLoadBalancer", "Launch", and "Terminate" processes on an ASG. If Eureka/Discovery is available, setting a status
 * override will also be achieved.
 */
class EnableDisableAsgDescription extends AbstractAmazonCredentialsDescription implements EnableDisableDescriptionTrait, ServerGroupsNameable {
  String region

  List<AsgDescription> asgs = []

  @Deprecated
  String asgName

  @Deprecated
  List<String> regions = []

  Integer desiredPercentage

  Integer targetHealthyDeployPercentage

  @Override
  Collection<String> getServerGroupNames() {
    return asgs.collect { it.serverGroupName }
  }
}
