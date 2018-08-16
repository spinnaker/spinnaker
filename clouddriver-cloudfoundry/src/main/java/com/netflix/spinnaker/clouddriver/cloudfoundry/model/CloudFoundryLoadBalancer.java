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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.clouddriver.cloudfoundry.CloudFoundryCloudProvider;
import com.netflix.spinnaker.clouddriver.model.LoadBalancer;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerInstance;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup;
import com.netflix.spinnaker.moniker.Moniker;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import javax.annotation.Nullable;
import java.util.Set;

import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toSet;

@RequiredArgsConstructor
@ToString
@EqualsAndHashCode(of = "id", callSuper = false)
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CloudFoundryLoadBalancer extends CloudFoundryModel implements LoadBalancer, Cloneable {
  private static final Moniker EMPTY_MONIKER = new Moniker();

  private final String account;
  private final String id;
  @Nullable
  private final String host;
  @Nullable
  private final String path;
  @Nullable
  private final Integer port;
  private final CloudFoundrySpace space;
  private final CloudFoundryDomain domain;

  @JsonIgnore
  private final Set<CloudFoundryServerGroup> mappedApps;

  @JsonProperty
  public String getName() {
    return host + "." + domain + "." + getName() + (port == null ? "" : "-" + port) + (path == null ? "" : "/" + path);
  }

  @Override
  public Moniker getMoniker() {
    return EMPTY_MONIKER;
  }

  @Override
  public Set<LoadBalancerServerGroup> getServerGroups() {
    return mappedApps.stream().map(app ->
      new LoadBalancerServerGroup(
        app.getName(),
        account,
        app.getRegion(),
        app.getState() == CloudFoundryServerGroup.State.STOPPED,
        emptySet(),
        app.getInstances()
          .stream()
          .map(it -> new LoadBalancerInstance(it.getId(), it.getName(), null, it.getHealth().get(0)))
          .collect(toSet())
      )
    ).collect(toSet());
  }

  @Deprecated
  public String getType() {
    return CloudFoundryCloudProvider.ID;
  }

  public String getRegion() {
    return space != null ? space.getRegion() : null;
  }
}
