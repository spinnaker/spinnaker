/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup

import com.amazonaws.services.ec2.model.IpPermission
import com.fasterxml.jackson.annotation.JsonProperty

class MigrateSecurityGroupResult {
  Collection<MigrateSecurityGroupReference> skipped = []
  Collection<MigrateSecurityGroupReference> warnings = []
  Collection<MigrateSecurityGroupReference> created = []
  Collection<MigrateSecurityGroupReference> reused = []
  Collection<MigrateSecurityGroupReference> errors = []
  MigrateSecurityGroupReference target
  Collection<IpPermission> ingressUpdates = []

  @JsonProperty("targetExists")
  boolean targetExists() {
    reused.contains(target)
  }
}
