/*
 * Copyright 2019 Alibaba Group.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.alicloud.model;

import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import java.util.Map;
import java.util.Set;
import lombok.Data;

@Data
public class AliCloudServerGroup implements ServerGroup {

  private String name;
  private String type;
  private String cloudProvider;
  private String region;
  private boolean disabled;
  private Long createdTime;
  private Set<String> zones;
  private Set<AliCloudInstance> instances;
  private Set<String> loadBalancers;
  private Set<String> securityGroups;
  private Map<String, Object> launchConfig;
  private InstanceCounts instanceCounts;
  private Capacity capacity;
  private ImageSummary imageSummary;
  private ImagesSummary imagesSummary;
  private String creationTime;
  private Map<String, Object> result;

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getType() {
    return type;
  }

  @Override
  public String getCloudProvider() {
    return cloudProvider;
  }

  @Override
  public String getRegion() {
    return region;
  }

  @Override
  public Boolean isDisabled() {
    return disabled;
  }

  @Override
  public Long getCreatedTime() {
    return createdTime;
  }

  @Override
  public Set<String> getZones() {
    return zones;
  }

  @Override
  public Set<AliCloudInstance> getInstances() {
    return instances;
  }

  @Override
  public Set<String> getLoadBalancers() {
    return loadBalancers;
  }

  @Override
  public Set<String> getSecurityGroups() {
    return securityGroups;
  }

  @Override
  public Map<String, Object> getLaunchConfig() {
    return launchConfig;
  }

  @Override
  public InstanceCounts getInstanceCounts() {
    return instanceCounts;
  }

  @Override
  public Capacity getCapacity() {
    return capacity;
  }

  @Override
  public ImageSummary getImageSummary() {
    return imageSummary;
  }

  @Override
  public ImagesSummary getImagesSummary() {
    return imagesSummary;
  }
}
