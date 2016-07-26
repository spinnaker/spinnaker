/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops.servergroup;

import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.MigrateLoadBalancerResult;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.MigrateSecurityGroupResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MigrateClusterConfigurationResult {

  private Map<String, Object> cluster;
  private List<MigrateSecurityGroupResult> securityGroupMigrations;
  private List<MigrateLoadBalancerResult> loadBalancerMigrations;
  private List<String> warnings = new ArrayList<>();

  public Map<String, Object> getCluster() {
    return cluster;
  }

  public void setCluster(Map<String, Object> cluster) {
    this.cluster = cluster;
  }

  public List<MigrateSecurityGroupResult> getSecurityGroupMigrations() {
    return securityGroupMigrations;
  }

  public void setSecurityGroupMigrations(List<MigrateSecurityGroupResult> securityGroupMigrations) {
    this.securityGroupMigrations = securityGroupMigrations;
  }

  public List<MigrateLoadBalancerResult> getLoadBalancerMigrations() {
    return loadBalancerMigrations;
  }

  public void setLoadBalancerMigrations(List<MigrateLoadBalancerResult> loadBalancerMigrations) {
    this.loadBalancerMigrations = loadBalancerMigrations;
  }

  public List<String> getWarnings() {
    return warnings;
  }


}
