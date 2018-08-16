/*
 * Copyright 2018 Pivotal, Inc.
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
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.model;

import com.netflix.frigga.Names;
import com.netflix.spinnaker.clouddriver.cloudfoundry.CloudFoundryCloudProvider;
import com.netflix.spinnaker.clouddriver.model.Cluster;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Set;

@AllArgsConstructor
@EqualsAndHashCode(of = {"name", "accountName"}, callSuper = false)
@Getter
public class CloudFoundryCluster extends CloudFoundryModel implements Cluster {
  private final String accountName;
  private final String name;
  private final Set<CloudFoundryServerGroup> serverGroups;
  private final Set<CloudFoundryLoadBalancer> loadBalancers;

  public String getStack() {
    return Names.parseName(name).getStack();
  }

  public String getDetail() {
    return Names.parseName(name).getDetail();
  }

  public String getType() {
    return CloudFoundryCloudProvider.ID;
  }
}