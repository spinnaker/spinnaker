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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.run.v1.model.Service;
import com.netflix.spinnaker.clouddriver.cloudrun.CloudrunCloudProvider;
import com.netflix.spinnaker.clouddriver.model.LoadBalancer;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerInstance;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup;
import com.netflix.spinnaker.moniker.Moniker;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CloudrunLoadBalancer implements LoadBalancer, Serializable {
  private String name;
  private String selfLink;
  private String region;
  private final String type = CloudrunCloudProvider.ID;
  private final String cloudProvider = CloudrunCloudProvider.ID;
  private String account;
  private Set<LoadBalancerServerGroup> serverGroups = new HashSet<>();
  private CloudrunTrafficSplit split = new CloudrunTrafficSplit();
  private String url;
  private String project;

  private String latestReadyRevisionName;

  private String latestCreatedRevisionName;

  public void setMoniker(Moniker _ignored) {}

  public CloudrunLoadBalancer() {}

  public CloudrunLoadBalancer(Service service, String account, String region) {
    this.name = service.getMetadata().getName();
    this.selfLink = service.getMetadata().getSelfLink();
    this.account = account;
    this.region = region;
    if (service.getStatus().getTraffic() != null) {
      this.split
          .getTrafficTargets()
          .addAll(
              new ObjectMapper()
                  .convertValue(
                      service.getStatus().getTraffic(),
                      new TypeReference<List<CloudrunTrafficSplit.TrafficTarget>>() {}));
    }
    this.url = service.getStatus().getUrl();
    this.latestCreatedRevisionName = service.getStatus().getLatestCreatedRevisionName();
    this.latestReadyRevisionName = service.getStatus().getLatestReadyRevisionName();
    this.project = service.getMetadata().getNamespace(); // project number
  }

  public void setLoadBalancerServerGroups(Set<CloudrunServerGroup> serverGroups) {
    Set<LoadBalancerServerGroup> loadBalancerServerGroups = new HashSet<>();
    serverGroups.forEach(
        serverGroup -> {
          Set<LoadBalancerInstance> instances = new HashSet<>();
          if (!serverGroup.isDisabled()) {
            serverGroup
                .getInstances()
                .forEach(
                    instance -> {
                      Map<String, Object> health = new HashMap<>();
                      health.put("state", instance.getHealthState().toString());
                      instances.add(
                          new LoadBalancerInstance().setId(instance.getName()).setHealth(health));
                    });
          }

          Set<String> detachedInstances = new HashSet<>();
          if (serverGroup.isDisabled()) {
            detachedInstances.addAll(
                serverGroup.getInstances().stream()
                    .map(CloudrunInstance::getName)
                    .collect(Collectors.toSet()));
          }

          loadBalancerServerGroups.add(
              new CloudrunLoadBalancerServerGroup()
                  .setName(serverGroup.getName())
                  .setRegion(serverGroup.getRegion())
                  .setIsDisabled(serverGroup.isDisabled())
                  .setInstances(instances)
                  .setDetachedInstances(detachedInstances)
                  .setCloudProvider(CloudrunCloudProvider.ID));
        });
    this.serverGroups.addAll(loadBalancerServerGroups);
  }

  public static class CloudrunLoadBalancerServerGroup extends LoadBalancerServerGroup {}
}
