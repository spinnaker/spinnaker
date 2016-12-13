/*
 * Copyright 2016 Veritas Technologies LLC.
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

package com.netflix.spinnaker.clouddriver.openstack.deploy.ops.servergroup

import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.EnableDisableAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations

/**
 * curl -X POST -H "Content-Type: application/json" -d '[ { "disableServerGroup": { "serverGroupName": "myapp-teststack-v006", "region": "RegionOne", "account": "test" }} ]' localhost:7002/openstack/ops
 */
class DisableOpenstackAtomicOperation extends AbstractEnableDisableOpenstackAtomicOperation {
  final String phaseName = "DISABLE_SERVER_GROUP"
  final String operation = AtomicOperations.DISABLE_SERVER_GROUP
  DisableOpenstackAtomicOperation(EnableDisableAtomicOperationDescription description) {
    super(description)
  }

  @Override
  boolean isDisable() {
    true
  }

}
