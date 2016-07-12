/*
 * Copyright 2016 Target Inc.
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

package com.netflix.spinnaker.clouddriver.openstack.model

import com.google.common.collect.Sets
import com.netflix.spinnaker.clouddriver.model.Cluster
import com.netflix.spinnaker.clouddriver.openstack.OpenstackCloudProvider
import groovy.transform.EqualsAndHashCode

@EqualsAndHashCode
class OpenstackCluster implements Cluster, Serializable {
  String accountName
  String name
  Set<OpenstackServerGroup> serverGroups
  Set<OpenstackLoadBalancer> loadBalancers
  String type = OpenstackCloudProvider.ID

  OpenstackCluster() {
    this.serverGroups = Sets.newConcurrentHashSet()
    this.loadBalancers = Sets.newConcurrentHashSet()
  }
}
