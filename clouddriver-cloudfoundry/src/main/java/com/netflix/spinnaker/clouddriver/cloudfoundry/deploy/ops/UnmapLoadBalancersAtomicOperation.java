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

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryApiException;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.Routes;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.LoadBalancersDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryLoadBalancer;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class UnmapLoadBalancersAtomicOperation implements AtomicOperation<Void> {
  public static final String PHASE = "UNMAP_LOAD_BALANCERS";

  private final LoadBalancersDescription description;

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public Void operate(List priorOutputs) {
    getTask()
        .updateStatus(
            PHASE, "Unmapping '" + description.getServerGroupName() + "' from load balancer(s).");

    List<String> routeList = description.getRoutes();
    if (routeList == null || routeList.size() == 0) {
      throw new CloudFoundryApiException("No load balancer specified");
    } else {
      Routes routes = description.getClient().getRoutes();
      Map<String, Optional<CloudFoundryLoadBalancer>> lbMap =
          routeList.stream()
              .collect(
                  Collectors.toMap(
                      uri -> uri,
                      uri ->
                          Optional.ofNullable(
                              routes.find(routes.toRouteId(uri), description.getSpace().getId()))));
      routeList.forEach(
          uri -> {
            if (!Routes.isValidRouteFormat(uri)) {
              throw new CloudFoundryApiException("Invalid format for load balancer '" + uri + "'");
            } else if (!lbMap.get(uri).isPresent()) {
              throw new CloudFoundryApiException("Load balancer '" + uri + "' does not exist");
            }
          });

      CloudFoundryClient client = description.getClient();
      lbMap.forEach(
          (uri, o) -> {
            getTask().updateStatus(PHASE, "Unmapping load balancer '" + uri + "'");
            o.ifPresent(
                lb ->
                    client
                        .getApplications()
                        .unmapRoute(description.getServerGroupId(), lb.getId()));
            getTask().updateStatus(PHASE, "Unmapped load balancer '" + uri + "'");
          });
    }

    return null;
  }
}
