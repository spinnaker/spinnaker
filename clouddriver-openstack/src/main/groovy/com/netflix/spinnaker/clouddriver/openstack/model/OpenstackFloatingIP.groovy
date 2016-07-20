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

package com.netflix.spinnaker.clouddriver.openstack.model

import groovy.transform.Canonical
import org.openstack4j.model.compute.FloatingIP

@Canonical
class OpenstackFloatingIP {
  String id
  String pool
  String instanceId
  String fixedIpAddress
  String floatingIpAddress
  String account
  String region

  /**
   * Produce a domain specific floating IP from an openstack floating IP.
   * @param vip
   * @return
   */
  static OpenstackFloatingIP from(FloatingIP ip, String account, String region) {
    new OpenstackFloatingIP(id: ip.id, pool: ip.pool, instanceId: ip.instanceId, fixedIpAddress: ip.fixedIpAddress,
      floatingIpAddress: ip.floatingIpAddress, account: account, region: region)
  }

}
