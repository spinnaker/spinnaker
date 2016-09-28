/*
 * Copyright 2016 Target, Inc.
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

package com.netflix.spinnaker.clouddriver.openstack.deploy.ops.discovery

import com.netflix.spinnaker.clouddriver.openstack.deploy.description.instance.OpenstackInstancesDescription

/**
 * curl -X POST -H "Content-Type: application/json" -d '[ { "disableInstancesInDiscovery": { "instanceIds": ["155e68a7-a7dd-433a-b2c1-c8d6d38fb89a"], "region": "RegionOne", "account": "my-openstack-account" }} ]' localhost:7002/openstack/ops
 */
class DisableInstancesInDiscoveryOperation extends AbstractEnableDisableInstancesInDiscoveryOperation {
  boolean disable = true
  String phaseName = 'DISABLE_INSTANCES_IN_DISCOVERY'

  DisableInstancesInDiscoveryOperation(OpenstackInstancesDescription description) {
    super(description)
  }
}
