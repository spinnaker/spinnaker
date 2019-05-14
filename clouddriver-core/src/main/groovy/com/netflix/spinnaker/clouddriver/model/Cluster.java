/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.netflix.spinnaker.clouddriver.documentation.Empty;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.moniker.Moniker;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A cluster is an object that provides an association between an account, many server groups, and
 * many load balancers.
 */
public interface Cluster {
  /**
   * The name of the cluster
   *
   * @return the cluster name
   */
  String getName();

  /**
   * This resource's moniker
   *
   * @return
   */
  default Moniker getMoniker() {
    return NamerRegistry.getDefaultNamer().deriveMoniker(this);
  }

  /**
   * The type of the cluster -- may be used for datacenter awareness
   *
   * @return the cluster type
   */
  String getType();

  /**
   * The account name to which this cluster is associated
   *
   * @return account name
   */
  String getAccountName();

  /**
   * A set of {@link ServerGroup} objects that comprise this cluster
   *
   * @return a set of {@link ServerGroup} objects or an empty set if none exist
   */
  @Empty
  @JsonSerialize(nullsUsing = NullCollectionSerializer.class)
  Set<? extends ServerGroup> getServerGroups();

  /**
   * A set of {@link LoadBalancer} objects that are associated with this cluster
   *
   * @return a set of {@link LoadBalancer} objects or an empty set if none exist
   */
  @Empty
  @JsonSerialize(nullsUsing = NullCollectionSerializer.class)
  // TODO(ttomsu): Why are load balancers associated with Clusters instead of ServerGroups?
  Set<? extends LoadBalancer> getLoadBalancers();

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  class SimpleCluster implements Cluster {
    String name;
    String type;
    String accountName;
    Set<ServerGroup> serverGroups;
    Set<LoadBalancer> loadBalancers;
  }
}
