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
import org.openstack4j.model.network.Port

@Canonical
class OpenstackPort {
  String deviceId
  String name
  String networkId
  List<String> securityGroups
  String account
  String region

  /**
   * Convert an openstack Port into a domain specific object.
   * @param port
   * @return
   */
  static OpenstackPort from(Port port, String account, String region) {
    new OpenstackPort(deviceId: port.deviceId, name: port.name, networkId: port.networkId,
      securityGroups: port.securityGroups, account: account, region: region)
  }
}
