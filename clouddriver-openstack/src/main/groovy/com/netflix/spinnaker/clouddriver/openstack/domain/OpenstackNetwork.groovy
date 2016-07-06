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

package com.netflix.spinnaker.clouddriver.openstack.domain

import com.netflix.spinnaker.clouddriver.model.Network
import com.netflix.spinnaker.clouddriver.openstack.OpenstackCloudProvider

class OpenstackNetwork implements Network {

  //core attributes
  String cloudProvider = OpenstackCloudProvider.ID
  String id
  String name
  String account
  String region

  //openstack attribute extensions
  Boolean external

  /**
   * Convert an openstack4j object into a spinnaker openstack domain object.
   * @param network
   * @param account
   * @param region
   * @return
   */
  static OpenstackNetwork from(org.openstack4j.model.network.Network network, String account, String region) {
    new OpenstackNetwork(id: network.id, name: network.name, external: network.routerExternal, account: account, region: region)
  }

}
