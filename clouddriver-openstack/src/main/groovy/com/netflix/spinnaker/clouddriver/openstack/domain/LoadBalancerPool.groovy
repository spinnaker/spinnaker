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

package com.netflix.spinnaker.clouddriver.openstack.domain

import groovy.transform.AutoClone
import groovy.transform.Canonical
import org.openstack4j.model.network.ext.LbPool

@AutoClone
@Canonical
class LoadBalancerPool implements LoadBalancerResolver {
  String id
  String name
  LoadBalancerProtocol protocol
  LoadBalancerMethod method
  String subnetId
  Integer internalPort
  String description
  Long createdTime

  void setInternalPort(Integer port) {
    this.internalPort = port
    addToDescription(generateInternalPort(port))
  }

  void setInternalPortFromDescription(String description) {
    this.internalPort = parseInternalPort(description)
    addToDescription(generateInternalPort(internalPort))
  }

  void setCreatedTime(long time) {
    this.createdTime = time
    addToDescription(generateCreatedTime(createdTime))
  }

  void setCreatedTimeFromDescription(String description) {
    this.createdTime = parseCreatedTime(description)
    addToDescription(generateCreatedTime(createdTime))
  }

  boolean doesMethodMatch(String methodName) {
    method?.name() == methodName
  }

  boolean doesInternalPortMatch(String currentDescription) {
    currentDescription != null && this.internalPort == parseInternalPort(currentDescription)
  }

  boolean equals(LbPool lbPool) {
    doesMethodMatch(lbPool.lbMethod?.name()) && this.name == lbPool.name && doesInternalPortMatch(lbPool.description)
  }

  void addToDescription(String value) {
    if (this.description) {
      this.description += ",$value"
    } else {
      this.description = value
    }
  }
}
