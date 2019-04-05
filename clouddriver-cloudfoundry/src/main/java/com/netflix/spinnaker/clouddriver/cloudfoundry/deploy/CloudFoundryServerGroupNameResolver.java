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
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import com.netflix.spinnaker.clouddriver.helpers.AbstractServerGroupNameResolver;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@AllArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class CloudFoundryServerGroupNameResolver extends AbstractServerGroupNameResolver {
  private static final String PHASE = "DEPLOY";

  CloudFoundryClient client;
  CloudFoundrySpace space;

  @Override
  public String getPhase() {
    return PHASE;
  }

  @Override
  public String getRegion() {
    return space.getRegion();
  }

  /**
   * Since this is only used to determine the next server group sequence number, it is only important to find
   * the latest server group in this cluster.
   */
  @Override
  public List<TakenSlot> getTakenSlots(String clusterName) {
    return Optional.ofNullable(client.getApplications().getLatestServerGroup(clusterName, space.getId()))
      .map(app -> {
        Names names = Names.parseName(app.getEntity().getName());
        return Collections.singletonList(new TakenSlot(names.getCluster(), names.getSequence(),
          Date.from(app.getMetadata().getCreatedAt().toInstant())));
      })
      .orElse(Collections.emptyList());
  }
}
