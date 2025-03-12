/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.model;

import com.netflix.spinnaker.clouddriver.aws.model.AmazonTargetGroup;
import com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider;
import com.netflix.spinnaker.clouddriver.model.Cluster;
import com.netflix.spinnaker.clouddriver.model.LoadBalancer;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import lombok.Data;

@Data
public class EcsServerCluster implements Cluster {

  private final String type = EcsCloudProvider.ID;

  private String name;

  private String accountName;

  private Set<AmazonTargetGroup> targetGroups =
      Collections.synchronizedSet(new HashSet<AmazonTargetGroup>());
  private Set<ServerGroup> serverGroups = Collections.synchronizedSet(new HashSet<ServerGroup>());
  private Set<LoadBalancer> loadBalancers =
      Collections.synchronizedSet(new HashSet<LoadBalancer>());

  public EcsServerCluster() {}
}
