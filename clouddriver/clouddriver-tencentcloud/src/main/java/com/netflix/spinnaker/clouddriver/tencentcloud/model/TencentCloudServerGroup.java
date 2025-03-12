/*
 * Copyright 2019 THL A29 Limited, a Tencent company.
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

package com.netflix.spinnaker.clouddriver.tencentcloud.model;

import com.netflix.spinnaker.clouddriver.model.HealthState;
import com.netflix.spinnaker.clouddriver.model.Instance;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.clouddriver.tencentcloud.TencentCloudProvider;
import com.netflix.spinnaker.clouddriver.tencentcloud.client.AutoScalingClient;
import com.netflix.spinnaker.moniker.Moniker;
import com.tencentcloudapi.as.v20180419.models.AutoScalingGroup;
import com.tencentcloudapi.as.v20180419.models.ForwardLoadBalancer;
import com.tencentcloudapi.as.v20180419.models.ScalingPolicy;
import com.tencentcloudapi.as.v20180419.models.ScheduledAction;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Data;

@Data
public class TencentCloudServerGroup implements ServerGroup, TencentCloudBasicResource {

  private final String type = TencentCloudProvider.ID;
  private final String cloudProvider = TencentCloudProvider.ID;
  private String accountName;
  private String name;
  private String region;
  private Set<String> zones;
  private Set<TencentCloudInstance> instances = new HashSet<>();
  private Map<String, Object> image = new HashMap<>();
  private Map<String, Object> launchConfig = new HashMap<>();
  private AutoScalingGroup asg;
  private Map buildInfo;
  private String vpcId;
  private List<ScalingPolicy> scalingPolicies = new ArrayList<>();
  private List<ScheduledAction> scheduledActions = new ArrayList<>();
  private Boolean disabled = false;

  @Override
  public String getName() {
    return this.name;
  }

  @Override
  public Moniker getMoniker() {
    return NamerRegistry.lookup()
        .withProvider(TencentCloudProvider.ID)
        .withAccount(accountName)
        .withResource(TencentCloudBasicResource.class)
        .deriveMoniker(this);
  }

  @Override
  public String getType() {
    return this.type;
  }

  @Override
  public String getCloudProvider() {
    return this.cloudProvider;
  }

  @Override
  public String getRegion() {
    return this.region;
  }

  @Override
  public String getMonikerName() {
    return name;
  }

  @Override
  public Boolean isDisabled() {
    return disabled;
  }

  @Override
  public Long getCreatedTime() {
    Date dateTime = null;
    if (asg != null) {
      dateTime = AutoScalingClient.convertToIsoDateTime(asg.getCreatedTime());
    }
    return dateTime != null ? dateTime.getTime() : null;
  }

  @Override
  public Set<String> getZones() {
    return zones;
  }

  @Override
  public Set<TencentCloudInstance> getInstances() {
    return instances;
  }

  @Override
  public Set<String> getLoadBalancers() {
    Set<String> loadBalancerNames = new HashSet<>();
    if (asg != null && asg.getForwardLoadBalancerSet() != null) {
      loadBalancerNames =
          Arrays.stream(asg.getForwardLoadBalancerSet())
              .map(ForwardLoadBalancer::getListenerId)
              .collect(Collectors.toSet());
    }

    if (asg != null && asg.getLoadBalancerIdSet() != null) {
      loadBalancerNames.addAll(Arrays.asList(asg.getLoadBalancerIdSet()));
    }

    return loadBalancerNames;
  }

  @Override
  public Set<String> getSecurityGroups() {
    Set<String> securityGroups = new HashSet<>();
    if (launchConfig != null && launchConfig.containsKey("securityGroupIds")) {
      securityGroups.addAll((List<String>) launchConfig.get("securityGroupIds"));
    }
    return securityGroups;
  }

  @Override
  public Map<String, Object> getLaunchConfig() {
    return this.launchConfig;
  }

  @Override
  public InstanceCounts getInstanceCounts() {
    InstanceCounts counts = new InstanceCounts();
    counts.setTotal(instances.size());
    counts.setUp(filterInstancesByHealthState(instances, HealthState.Up).size());
    counts.setDown(filterInstancesByHealthState(instances, HealthState.Down).size());
    counts.setUnknown(filterInstancesByHealthState(instances, HealthState.Unknown).size());
    counts.setStarting(filterInstancesByHealthState(instances, HealthState.Starting).size());
    counts.setOutOfService(
        filterInstancesByHealthState(instances, HealthState.OutOfService).size());
    return counts;
  }

  @Override
  public Capacity getCapacity() {
    Capacity capacity = new Capacity();
    capacity.setMin(
        Math.toIntExact(asg != null && asg.getMinSize() != null ? asg.getMinSize() : 0));
    capacity.setMax(
        Math.toIntExact(asg != null && asg.getMaxSize() != null ? asg.getMaxSize() : 0));
    capacity.setDesired(
        Math.toIntExact(
            asg != null && asg.getDesiredCapacity() != null ? asg.getDesiredCapacity() : 0));
    return capacity;
  }

  @Override
  public ImagesSummary getImagesSummary() {
    return new ImagesSummary() {
      @Override
      public List<ImageSummary> getSummaries() {
        return new ArrayList<>(
            Arrays.asList(
                new ImageSummary() {
                  private String serverGroupName = getName();
                  private String imageName = (image == null ? null : (String) image.get("name"));
                  private String imageId = (image == null ? null : (String) image.get("imageId"));

                  @Override
                  public Map<String, Object> getBuildInfo() {
                    return ((Map<String, Object>) (buildInfo));
                  }

                  @Override
                  public Map<String, Object> getImage() {
                    return image;
                  }

                  public String getServerGroupName() {
                    return serverGroupName;
                  }

                  public void setServerGroupName(String serverGroupName) {
                    this.serverGroupName = serverGroupName;
                  }

                  public String getImageName() {
                    return imageName;
                  }

                  public void setImageName(String imageName) {
                    this.imageName = imageName;
                  }

                  public String getImageId() {
                    return imageId;
                  }

                  public void setImageId(String imageId) {
                    this.imageId = imageId;
                  }
                }));
      }
    };
  }

  @Override
  public ImageSummary getImageSummary() {
    final ImagesSummary summary = getImagesSummary();
    return summary.getSummaries().get(0);
  }

  public static Collection<Instance> filterInstancesByHealthState(
      Set<TencentCloudInstance> instances, HealthState healthState) {
    return instances.stream()
        .filter(it -> it.getHealthState() == healthState)
        .collect(Collectors.toList());
  }
}
