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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.netflix.frigga.Names;
import com.netflix.spinnaker.clouddriver.cloudfoundry.CloudFoundryCloudProvider;
import com.netflix.spinnaker.clouddriver.model.Image;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.moniker.Moniker;
import io.vavr.collection.HashMap;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.Wither;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.netflix.spinnaker.clouddriver.model.HealthState.*;
import static java.util.Collections.*;

@Value
@EqualsAndHashCode(of = "id", callSuper = false)
@Builder
@JsonDeserialize(builder = CloudFoundryServerGroup.CloudFoundryServerGroupBuilder.class)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties("loadBalancerNames")
public class CloudFoundryServerGroup extends CloudFoundryModel implements ServerGroup {
  private static final ObjectMapper IMAGE_MAPPER = new ObjectMapper();

  @JsonView(Views.Cache.class)
  String account;

  @JsonView(Views.Cache.class)
  String appsManagerUri;

  @JsonView(Views.Cache.class)
  String metricsUri;

  @JsonView(Views.Cache.class)
  String name;

  @JsonView(Views.Cache.class)
  String id;

  @JsonView(Views.Cache.class)
  Integer memory;

  @JsonView(Views.Cache.class)
  @Nullable
  CloudFoundryDroplet droplet;

  @JsonView(Views.Cache.class)
  Integer diskQuota;

  @JsonView(Views.Cache.class)
  @Nullable
  private String healthCheckType;

  @JsonView(Views.Cache.class)
  @Nullable
  private String healthCheckHttpEndpoint;

  @JsonView(Views.Cache.class)
  State state;

  @JsonView(Views.Cache.class)
  CloudFoundrySpace space;

  @JsonView(Views.Cache.class)
  Long createdTime;

  @JsonView(Views.Cache.class)
  Map<String, String> env;

  @Wither
  @JsonView(Views.Cache.class)
  List<CloudFoundryServiceInstance> serviceInstances;

  @Wither
  @JsonView(Views.Relationship.class)
  Set<CloudFoundryInstance> instances;

  @Wither
  @JsonView(Views.Relationship.class)
  Set<String> loadBalancerNames;

  @Override
  public Set<String> getLoadBalancers() {
    return loadBalancerNames == null ? emptySet() : loadBalancerNames;
  }

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
              return IMAGE_MAPPER.convertValue(this, new TypeReference<Map<String, Object>>() {
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
    return CloudFoundryImage.builder()
      .id(droplet == null ? "unknown" : droplet.getId())
      .name(name + "-droplet")
      .region(getRegion())
      .build();
  }

  public Map getBuildInfo() {
    return HashMap.<Object, Object>of(
      "appsManagerUri", appsManagerUri,
      "metricsUri", metricsUri,
      "droplet", droplet,
      "id", id,
      "serviceInstances", serviceInstances)
      .toJavaMap();
  }

  @Deprecated
  @Override
  public ImageSummary getImageSummary() {
    return getImagesSummary().getSummaries().get(0);
  }

  @Override
  public String getRegion() {
    return space == null ? "unknown" : space.getRegion();
  }

  @Override
  public Boolean isDisabled() {
    return state == State.STOPPED;
  }

  @Override
  public Set<String> getZones() {
    return space == null ? emptySet() : singleton(space.getName());
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

  @SuppressWarnings("unchecked")
  public Moniker getMoniker() {
    Moniker moniker = NamerRegistry.getDefaultNamer().deriveMoniker(this);
    return new Moniker(moniker.getApp(), moniker.getCluster(), moniker.getDetail(), moniker.getStack(),
      moniker.getSequence() == null ? 0 : moniker.getSequence());
  }

  @Deprecated
  public String getType() {
    return CloudFoundryCloudProvider.ID;
  }

  public enum State {
    STOPPED,
    STARTED
  }
}
