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

import static java.util.Collections.emptySet;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.netflix.frigga.Names;
import com.netflix.spinnaker.clouddriver.cloudfoundry.CloudFoundryCloudProvider;
import com.netflix.spinnaker.clouddriver.model.Cluster;
import com.netflix.spinnaker.clouddriver.model.LoadBalancer;
import java.util.Set;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.Wither;

@Value
@EqualsAndHashCode(
    of = {"name", "accountName"},
    callSuper = false)
@Builder
@JsonDeserialize(builder = CloudFoundryCluster.CloudFoundryClusterBuilder.class)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CloudFoundryCluster extends CloudFoundryModel implements Cluster {

  @JsonView(Views.Cache.class)
  String accountName;

  @JsonView(Views.Cache.class)
  String name;

  @Wither
  @JsonView(Views.Relationship.class)
  Set<CloudFoundryServerGroup> serverGroups;

  /**
   * Load balancers are read from the server group model, and don't make sense on cluster. There is
   * no practical impact to leaving this empty.
   */
  @Override
  public Set<? extends LoadBalancer> getLoadBalancers() {
    return emptySet();
  }

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
