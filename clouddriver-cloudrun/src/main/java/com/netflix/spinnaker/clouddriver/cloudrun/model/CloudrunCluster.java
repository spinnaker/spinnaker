/*
 * Copyright 2022 OpsMx, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.clouddriver.cloudrun.model;

import com.netflix.spinnaker.clouddriver.cloudrun.cache.Keys;
import com.netflix.spinnaker.clouddriver.model.Cluster;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CloudrunCluster implements Cluster, Serializable {
  private String name;
  private String type = Keys.Namespace.provider;
  private String accountName;
  private Set<CloudrunServerGroup> serverGroups =
      Collections.synchronizedSet(new HashSet<CloudrunServerGroup>());
  private Set<CloudrunLoadBalancer> loadBalancers =
      Collections.synchronizedSet(new HashSet<CloudrunLoadBalancer>());
}
