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

import com.netflix.spinnaker.clouddriver.model.ServerGroup
import com.netflix.spinnaker.clouddriver.security.resources.ServerGroupNameable

class ResizeAsgDescription extends AbstractAmazonCredentialsDescription implements ServerGroupNameable {
  ServerGroup.Capacity capacity
  List<AsgTargetDescription> asgs = []

  @Override
  Collection<String> getServerGroupNames() {
    return asgs.collect { it.serverGroupName }
  }

  static class Constraints {
    ServerGroup.Capacity capacity
  }

  static class AsgTargetDescription extends AsgDescription {
    ServerGroup.Capacity capacity = new ServerGroup.Capacity()
    Constraints constraints = new Constraints()

    @Override
    String toString() {
      "region: $region, serverGroupName: $serverGroupName, capacity: [$capacity]"
    }
  }
}
