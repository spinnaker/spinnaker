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

package com.netflix.spinnaker.clouddriver.cloudfoundry.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.frigga.Names;
import com.netflix.spinnaker.clouddriver.cloudfoundry.CloudFoundryCloudProvider;
import com.netflix.spinnaker.clouddriver.model.Image;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.moniker.Moniker;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.clouddriver.model.HealthState.*;
import static java.util.Collections.*;

@RequiredArgsConstructor
@ToString
@Getter
@EqualsAndHashCode(of = "id", callSuper = false)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CloudFoundryServerGroup extends CloudFoundryModel implements ServerGroup {
  private static final ObjectMapper mapper = new ObjectMapper();

  private final String account;
  private final String name;
  private final String id;
  private final Integer memory;
  private final Set<CloudFoundryInstance> instances;
  private final CloudFoundryDroplet droplet;
  private final Integer diskQuota;
  private final State state;
  private final CloudFoundrySpace space;
  private final Long createdTime;
  private final List<CloudFoundryLoadBalancer> routes;
  private final List<CloudFoundryServiceInstance> serviceInstances;

  @Override
  public ImagesSummary getImagesSummary() {
    return new ImagesSummary() {
      @Override
      public List<? extends ImageSummary> getSummaries() {
        return singletonList(
          new ImageSummary() {
            @Override
            public String getServerGroupName() {
              return name;
            }

            @Override
            public String getImageName() {
              return name + "-droplet";
            }

            @Override
            public String getImageId() {
              return droplet == null ? "unknown" : droplet.getId();
            }

            @Override
            public Map<String, Object> getImage() {
              return mapper.convertValue(this, new TypeReference<Map>() {
              });
            }

            @Override
            public Map<String, Object> getBuildInfo() {
              return emptyMap();
            }
          }
        );
      }
    };
  }

  public Image getImage() {
    return new CloudFoundryImage(droplet == null ? "unknown" : droplet.getId(), name + "-droplet", space.getRegion());
  }

  public Map getBuildInfo() {
    Map<String, Object> buildInfo = new HashMap<>();
    buildInfo.put("droplet", droplet);
    buildInfo.put("serviceInstances", serviceInstances);
    buildInfo.put("id", id);
    return buildInfo;
  }

  @Deprecated
  @Override
  public ImageSummary getImageSummary() {
    return getImagesSummary() != null ? getImagesSummary().getSummaries().get(0) : null;
  }

  @Override
  public String getRegion() {
    return space.getRegion();
  }

  @Override
  public Boolean isDisabled() {
    return state == State.STOPPED;
  }

  @Override
  public Set<String> getZones() {
    return singleton(space.getName());
  }

  @Override
  public Set<String> getLoadBalancers() {
    return routes.stream().map(CloudFoundryLoadBalancer::getName).collect(Collectors.toSet());
  }

  @Override
  public Set<String> getSecurityGroups() {
    return emptySet();
  }

  @Override
  public Map<String, Object> getLaunchConfig() {
    return emptyMap();
  }

  @Override
  public InstanceCounts getInstanceCounts() {
    return new InstanceCounts(
      instances.size(),
      (int) instances.stream().filter(in -> Up.equals(in.getHealthState())).count(),
      (int) instances.stream().filter(in -> Down.equals(in.getHealthState())).count(),
      (int) instances.stream().filter(in -> Unknown.equals(in.getHealthState())).count(),
      (int) instances.stream().filter(in -> OutOfService.equals(in.getHealthState())).count(),
      (int) instances.stream().filter(in -> Starting.equals(in.getHealthState())).count()
    );
  }

  @Override
  public Capacity getCapacity() {
    return new ServerGroup.Capacity(instances.size(), instances.size(), instances.size());
  }

  public String getStack() {
    return Names.parseName(name).getStack();
  }

  public String getDetail() {
    return Names.parseName(name).getDetail();
  }

  public Moniker getMoniker() {
    Moniker moniker = NamerRegistry.getDefaultNamer().deriveMoniker(this);
    return new Moniker(moniker.getApp(), moniker.getCluster(), moniker.getDetail(), moniker.getStack(),
      moniker.getSequence() == null ? 0 : moniker.getSequence());
  }

  @Deprecated
  public String getType() {
    return CloudFoundryCloudProvider.ID;
  }

  enum State {
    STOPPED,
    STARTED
  }
}
