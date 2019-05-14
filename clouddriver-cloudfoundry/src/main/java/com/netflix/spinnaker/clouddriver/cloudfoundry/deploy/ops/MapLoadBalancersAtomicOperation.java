/*
 * Copyright 2019 Pivotal, Inc.
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

package com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.ops;

import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.LoadBalancersDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MapLoadBalancersAtomicOperation
    extends AbstractCloudFoundryLoadBalancerMappingOperation implements AtomicOperation<Void> {

  public static final String PHASE = "MAP_LOAD_BALANCERS";

  @Override
  protected String getPhase() {
    return PHASE;
  }

  private final LoadBalancersDescription description;

  @Override
  public Void operate(List priorOutputs) {
    getTask()
        .updateStatus(
            PHASE, "Mapping '" + description.getServerGroupName() + "' with loadbalancer(s).");

    if (mapRoutes(
        description,
        description.getRoutes(),
        description.getSpace(),
        description.getServerGroupId())) {
      getTask()
          .updateStatus(
              PHASE, "Mapped '" + description.getServerGroupName() + "' with loadbalancer(s).");
    }

    return null;
  }
}
