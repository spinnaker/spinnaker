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

package com.netflix.spinnaker.clouddriver.google.deploy.description

import com.netflix.spinnaker.clouddriver.deploy.description.EnableDisableDescriptionTrait
import com.netflix.spinnaker.clouddriver.security.resources.ServerGroupsNameable

/**
 * "Enabling" means adding a server group to the target pool of each of its network load balancers.
 *
 * "Disabling" means removing a server group from the target pool of each of its network load balancers.
 */
class EnableDisableGoogleServerGroupDescription extends AbstractGoogleCredentialsDescription implements ServerGroupsNameable, EnableDisableDescriptionTrait {
  String region
  String accountName

  @Deprecated
  String zone

  @Override
  Collection<String> getServerGroupNames() {
    return [getServerGroupName()]
  }
}
