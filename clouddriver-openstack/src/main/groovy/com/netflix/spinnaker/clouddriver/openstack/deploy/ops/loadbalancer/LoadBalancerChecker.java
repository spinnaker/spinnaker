/*
 * Copyright 2018 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.deploy.ops.loadbalancer;

import com.netflix.spinnaker.clouddriver.openstack.client.BlockingStatusChecker;
import com.netflix.spinnaker.clouddriver.openstack.config.OpenstackConfigurationProperties.LbaasConfig;
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.network.ext.LbProvisioningStatus;
import org.openstack4j.model.network.ext.LoadBalancerV2;

class LoadBalancerChecker implements BlockingStatusChecker.StatusChecker<LoadBalancerV2> {
  Operation operation;

  enum Operation {
    CREATE,
    UPDATE,
    DELETE
  }

  LoadBalancerChecker(Operation operation) {
    this.operation = operation;
  }

  @Override
  public boolean isReady(LoadBalancerV2 loadBalancer) {
    if (loadBalancer == null) {
      if (operation == Operation.DELETE) {
        return true;
      }
      ActionResponse actionResponse = ActionResponse.actionFailed("Cannot get status for null loadbalancer", 404);
      throw new OpenstackProviderException(actionResponse);
    }
    LbProvisioningStatus status = loadBalancer.getProvisioningStatus();
    if (status == LbProvisioningStatus.ERROR) {
      String failureMessage = String.format("Error in load balancer provision: %s, %s", loadBalancer.getName(), loadBalancer.getId());
      ActionResponse actionResponse = ActionResponse.actionFailed(failureMessage, 500);
      throw new OpenstackProviderException(actionResponse);
    }
    return status == LbProvisioningStatus.ACTIVE;
  }

  static BlockingStatusChecker from(LbaasConfig lbaasConfig, Operation operation) {
    LoadBalancerChecker checker = new LoadBalancerChecker(operation);
    return BlockingStatusChecker.from(lbaasConfig.getPollTimeout(), lbaasConfig.getPollInterval(), checker);
  }

}
