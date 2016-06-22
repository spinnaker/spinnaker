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

import groovy.transform.AutoClone
import groovy.transform.Canonical

/**
 * This class is a wrapper for parameters that are passed to an openstack heat template
 * when auto scaling groups are created.
 */
@AutoClone
@Canonical
class ServerGroupParameters {

  String instanceType
  String image
  Integer internalPort
  Integer maxSize
  Integer minSize
  String networkId
  String poolId
  List<String> securityGroups

  Map<String, String> toParamsMap() {
    [
      'flavor':instanceType,
      'image':image,
      'internal_port':internalPort ? internalPort.toString() : null,
      'max_size':maxSize ? maxSize.toString() : null,
      'min_size':minSize? minSize.toString() : null,
      'network_id':networkId,
      'pool_id':poolId,
      'security_groups':securityGroups ? securityGroups.join(',') : null
    ]
  }

  static ServerGroupParameters fromParamsMap(Map<String, String> params) {
    new ServerGroupParameters(
      instanceType: params.get('flavor'),
      image: params.get('image'),
      internalPort: params.get('internal_port')?.toInteger(),
      maxSize: params.get('max_size')?.toInteger(),
      minSize: params.get('min_size')?.toInteger(),
      networkId: params.get('network_id'),
      poolId: params.get('pool_id'),
      securityGroups: params.get('security_groups')?.split(',')?.toList()
    )
  }

}
