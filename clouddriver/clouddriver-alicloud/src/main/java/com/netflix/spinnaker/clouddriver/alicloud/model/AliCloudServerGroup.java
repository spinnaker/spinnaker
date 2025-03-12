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
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Data;

@Data
public class AliCloudServerGroup implements ServerGroup, Serializable {

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
  private String creationTime;
  private Map<String, Object> result;
  private Map<String, Object> image;
  private Map buildInfo;

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

  public Map<String, Object> getImage() {
    return image;
  }

  public void setImage(Map<String, Object> image) {
    this.image = image;
  }

  public Map getBuildInfo() {
    return buildInfo;
  }

  public void setBuildInfo(Map buildInfo) {
    this.buildInfo = buildInfo;
  }

  @Override
  public ImageSummary getImageSummary() {
    return getImagesSummary().getSummaries().get(0);
  }

  @Override
  public ImagesSummary getImagesSummary() {
    return new ImagesSummary() {
      @Override
      public List<? extends ImageSummary> getSummaries() {
        List<ImageSummary> list = new ArrayList<>();
        InnSum innSum = new InnSum(image, buildInfo, name);
        list.add(innSum);
        return list;
      }
    };
  }

  public class InnSum implements ImageSummary {

    private Map<String, Object> i;
    private Map bi;
    private String serverGroupName;

    public InnSum(Map<String, Object> i, Map bi, String serverGroupName) {
      this.i = i;
      this.bi = bi;
      this.serverGroupName = serverGroupName;
    }

    @Override
    public String getServerGroupName() {
      return serverGroupName;
    }

    @Override
    public String getImageId() {
      return String.valueOf(i.get("imageId"));
    }

    @Override
    public String getImageName() {
      return String.valueOf(i.get("name"));
    }

    @Override
    public Map<String, Object> getImage() {
      return i;
    }

    @Override
    public Map<String, Object> getBuildInfo() {
      return bi;
    }
  }
}
