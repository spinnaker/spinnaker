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

package com.netflix.spinnaker.clouddriver.cloudfoundry.deploy;

import com.netflix.frigga.Names;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import com.netflix.spinnaker.clouddriver.cloudfoundry.provider.view.CloudFoundryClusterProvider;
import com.netflix.spinnaker.clouddriver.helpers.AbstractServerGroupNameResolver;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.Date;
import java.util.List;

import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toList;

@AllArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class CloudFoundryServerGroupNameResolver extends AbstractServerGroupNameResolver {
  private static final String PHASE = "DEPLOY";

  String account;
  CloudFoundryClusterProvider clusters;
  CloudFoundrySpace space;

  @Override
  public String getPhase() {
    return PHASE;
  }

  @Override
  public String getRegion() {
    return space.getRegion();
  }

  @Override
  public List<TakenSlot> getTakenSlots(String clusterName) {
    return clusters.getClusters()
      .getOrDefault(account, emptySet())
      .stream()
      .flatMap(cluster -> cluster.getServerGroups().stream())
      .filter(serverGroup -> {
        Names names = Names.parseName(serverGroup.getName());
        return clusterName.equals(names.getCluster()) && getRegion().equals(serverGroup.getRegion());
      })
      .map(serverGroup -> {
        Names names = Names.parseName(serverGroup.getName());
        return new TakenSlot(serverGroup.getName(), names.getSequence(),
          serverGroup.getCreatedTime() == null ? null : new Date(serverGroup.getCreatedTime()));
      })
      .collect(toList());
  }
}
