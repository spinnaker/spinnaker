/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.converters;

import com.netflix.spinnaker.clouddriver.cloudfoundry.CloudFoundryOperation;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.RouteId;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.DeleteCloudFoundryLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.ops.DeleteCloudFoundryLoadBalancerAtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import static java.util.function.Function.identity;

@CloudFoundryOperation(AtomicOperations.DELETE_LOAD_BALANCER)
@Component
public class DeleteCloudFoundryLoadBalancerAtomicOperationConverter extends AbstractCloudFoundryAtomicOperationConverter {
  @Override
  public AtomicOperation convertOperation(Map input) {
    return new DeleteCloudFoundryLoadBalancerAtomicOperation(convertDescription(input));
  }

  @SuppressWarnings("unchecked")
  @Override
  public DeleteCloudFoundryLoadBalancerDescription convertDescription(Map input) {
    DeleteCloudFoundryLoadBalancerDescription converted = getObjectMapper().convertValue(input, DeleteCloudFoundryLoadBalancerDescription.class);
    converted.setClient(getClient(input));

    CloudFoundryClient client = converted.getClient();
    return ((Collection<String>) input.get("regions")).stream()
      .map(region -> findSpace(region, client))
      .filter(Optional::isPresent)
      .findFirst()
      .flatMap(identity())
      .map(space -> {
        String routePath = input.get("loadBalancerName").toString();
        RouteId routeId = client.getRoutes().toRouteId(routePath);
        if (routeId == null) {
          throw new IllegalArgumentException("Invalid format or domain for route '" + routePath + "'");
        }
        return converted.setLoadBalancer(client.getRoutes().find(routeId, space.getId()));
      })
      .orElseThrow(() -> new IllegalArgumentException("Unable to find the space(s) that this load balancer was expected to be in."));
  }
}
