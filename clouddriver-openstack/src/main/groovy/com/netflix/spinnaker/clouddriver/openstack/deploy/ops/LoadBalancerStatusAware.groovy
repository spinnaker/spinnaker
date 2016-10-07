/*
 * Copyright 2016 Target, Inc.
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

package com.netflix.spinnaker.clouddriver.openstack.deploy.ops

import com.netflix.spinnaker.clouddriver.openstack.client.BlockingStatusChecker
import com.netflix.spinnaker.clouddriver.openstack.config.OpenstackConfigurationProperties
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackCredentials
import org.openstack4j.model.network.ext.LbProvisioningStatus
import org.openstack4j.model.network.ext.LoadBalancerV2

/**
 * Helper methods to check on load balancer statuses.
 */
trait LoadBalancerStatusAware {

  /**
   * Creates and returns a new blocking active status checker.
   * @param region
   * @param loadBalancerId
   * @return
   */
  BlockingStatusChecker createBlockingActiveStatusChecker(OpenstackCredentials credentials, String region, String loadBalancerId = null) {
    OpenstackConfigurationProperties.LbaasConfig config = credentials.credentials.lbaasConfig
    BlockingStatusChecker.from(config.pollTimeout, config.pollInterval) { Object input ->
      String id = loadBalancerId
      if (!loadBalancerId && input instanceof LoadBalancerV2) {
        id = ((LoadBalancerV2) input).id
      }

      LbProvisioningStatus currentProvisioningStatus = credentials.provider.getLoadBalancer(region, id)?.provisioningStatus

      // Short circuit polling if openstack is unable to provision the load balancer
      if (LbProvisioningStatus.ERROR == currentProvisioningStatus) {
        throw new OpenstackProviderException("Openstack was unable to provision load balancer ${loadBalancerId}")
      }

      LbProvisioningStatus.ACTIVE == currentProvisioningStatus
    }
  }
}
