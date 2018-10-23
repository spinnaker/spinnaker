/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.deploy.description;

import com.netflix.spinnaker.clouddriver.aws.model.AmazonLoadBalancerType;

import java.util.List;
import java.util.Map;

public class UpsertAmazonLoadBalancerDescription extends AbstractAmazonCredentialsDescription {
  private AmazonLoadBalancerType loadBalancerType = AmazonLoadBalancerType.CLASSIC;

  private String clusterName;
  private String name;
  private String vpcId;
  private Boolean isInternal;
  private String subnetType;
  private Integer idleTimeout = 60;

  private List<String> securityGroups;
  private Map<String, List<String>> availabilityZones;

  private boolean shieldProtectionEnabled = true;

  public AmazonLoadBalancerType getLoadBalancerType() {
    return loadBalancerType;
  }

  public void setLoadBalancerType(AmazonLoadBalancerType loadBalancerType) {
    this.loadBalancerType = loadBalancerType;
  }

  public String getClusterName() {
    return clusterName;
  }

  public void setClusterName(String clusterName) {
    this.clusterName = clusterName;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getVpcId() {
    return vpcId;
  }

  public void setVpcId(String vpcId) {
    this.vpcId = vpcId;
  }

  public Boolean getIsInternal() {
    return isInternal;
  }

  public void setIsInternal(Boolean internal) {
    isInternal = internal;
  }

  public String getSubnetType() {
    return subnetType;
  }

  public void setSubnetType(String subnetType) {
    this.subnetType = subnetType;
  }

  public List<String> getSecurityGroups() {
    return securityGroups;
  }

  public void setSecurityGroups(List<String> securityGroups) {
    this.securityGroups = securityGroups;
  }

  public Map<String, List<String>> getAvailabilityZones() {
    return availabilityZones;
  }

  public void setAvailabilityZones(Map<String, List<String>> availabilityZones) {
    this.availabilityZones = availabilityZones;
  }

  public boolean getShieldProtectionEnabled() {
    return shieldProtectionEnabled;
  }

  public void setShieldProtectionEnabled(boolean shieldProtectionEnabled) {
    this.shieldProtectionEnabled = shieldProtectionEnabled;
  }

  public Integer getIdleTimeout() { return idleTimeout; }

  public void setIdleTimeout(Integer idleTimeout) { this.idleTimeout = idleTimeout; }

}
