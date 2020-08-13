/*
 * Copyright 2020 Armory
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

package com.netflix.spinnaker.clouddriver.jackson.mixins;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.netflix.spinnaker.clouddriver.model.LoadBalancer;
import com.netflix.spinnaker.clouddriver.model.NullCollectionSerializer;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import java.util.Map;
import java.util.Set;

public interface ClusterMixin {

  @JsonSerialize(nullsUsing = NullCollectionSerializer.class)
  Set<? extends ServerGroup> getServerGroups();

  @JsonSerialize(nullsUsing = NullCollectionSerializer.class)
  Set<? extends LoadBalancer> getLoadBalancers();

  @JsonIgnore
  Map<String, Object> getExtraAttributes();
}
