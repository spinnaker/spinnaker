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

import static java.util.stream.Collectors.toList;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryApiException;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.RouteId;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.AbstractCloudFoundryServerGroupDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryLoadBalancer;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

public abstract class AbstractCloudFoundryLoadBalancerMappingOperation {
  protected abstract String getPhase();

  protected static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  // VisibleForTesting
  boolean mapRoutes(
      AbstractCloudFoundryServerGroupDescription description,
      @Nullable List<String> routes,
      CloudFoundrySpace space,
      String serverGroupId) {
    if (routes == null) {
      getTask().updateStatus(getPhase(), "No load balancers provided to create or update");
      return true;
    }

    getTask().updateStatus(getPhase(), "Creating or updating load balancers");

    CloudFoundryClient client = description.getClient();
    List<RouteId> routeIds =
        routes.stream()
            .map(
                routePath -> {
                  RouteId routeId = client.getRoutes().toRouteId(routePath);
                  if (routeId == null) {
                    throw new IllegalArgumentException(routePath + " is an invalid route");
                  }
                  return routeId;
                })
            .filter(Objects::nonNull)
            .collect(toList());

    for (RouteId routeId : routeIds) {
      CloudFoundryLoadBalancer loadBalancer =
          client.getRoutes().createRoute(routeId, space.getId());
      if (loadBalancer == null) {
        throw new CloudFoundryApiException(
            "Load balancer already exists in another organization and space");
      }
      getTask()
          .updateStatus(
              getPhase(),
              "Mapping load balancer '"
                  + loadBalancer.getName()
                  + "' to "
                  + description.getServerGroupName());
      client.getApplications().mapRoute(serverGroupId, loadBalancer.getId());
    }

    return true;
  }
}
