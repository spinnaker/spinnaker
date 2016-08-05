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

import com.netflix.frigga.autoscaling.AutoScalingGroupNameBuilder;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.AbstractAmazonCredentialsDescription;
import com.netflix.spinnaker.clouddriver.aws.deploy.handlers.MigrateClusterConfigurationStrategy;
import com.netflix.spinnaker.clouddriver.aws.deploy.handlers.MigrateLoadBalancerStrategy;
import com.netflix.spinnaker.clouddriver.aws.deploy.handlers.MigrateSecurityGroupStrategy;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory.SecurityGroupLookup;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;

import java.util.List;
import java.util.Map;

public class ClusterConfigurationMigrator {

  private static final String BASE_PHASE = "MIGRATE_CLUSTER_CONFIG";

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  private ClusterConfiguration source;
  private ClusterConfigurationTarget target;
  private MigrateClusterConfigurationStrategy migrationStrategy;
  private MigrateLoadBalancerStrategy migrateLoadBalancerStrategy;
  private MigrateSecurityGroupStrategy migrateSecurityGroupStrategy;
  private SecurityGroupLookup sourceLookup;
  private SecurityGroupLookup targetLookup;
  private String iamRole;
  private String keyPair;
  private String subnetType;
  private String elbSubnetType;
  private Map<String, String> loadBalancerNameMapping;
  private boolean allowIngressFromClassic;

  public ClusterConfigurationMigrator(MigrateClusterConfigurationStrategy migrationStrategy,
                                      ClusterConfiguration source, ClusterConfigurationTarget target,
                                      SecurityGroupLookup sourceLookup, SecurityGroupLookup targetLookup,
                                      MigrateLoadBalancerStrategy migrateLoadBalancerStrategy,
                                      MigrateSecurityGroupStrategy migrateSecurityGroupStrategy,
                                      String iamRole, String keyPair, String subnetType, String elbSubnetType,
                                      Map<String, String> loadBalancerNameMapping,
                                      boolean allowIngressFromClassic) {
    this.migrationStrategy = migrationStrategy;
    this.source = source;
    this.target = target;
    this.sourceLookup = sourceLookup;
    this.targetLookup = targetLookup;
    this.migrateLoadBalancerStrategy = migrateLoadBalancerStrategy;
    this.migrateSecurityGroupStrategy = migrateSecurityGroupStrategy;
    this.iamRole = iamRole;
    this.keyPair = keyPair;
    this.subnetType = subnetType;
    this.elbSubnetType = elbSubnetType;
    this.loadBalancerNameMapping = loadBalancerNameMapping;
    this.allowIngressFromClassic = allowIngressFromClassic;
  }

  public MigrateClusterConfigurationResult migrate(boolean dryRun) {
    getTask().updateStatus(BASE_PHASE, (dryRun ? "Calculating" : "Beginning") + " migration of cluster config " + source.toString());
    MigrateClusterConfigurationResult result = migrationStrategy.generateResults(source, target, sourceLookup, targetLookup, migrateLoadBalancerStrategy,
      migrateSecurityGroupStrategy, subnetType, elbSubnetType, iamRole, keyPair, loadBalancerNameMapping, allowIngressFromClassic, dryRun);
    getTask().updateStatus(BASE_PHASE, "Migration of cluster configuration " + source.toString() +
      (dryRun ? " calculated" : " completed") + ".");
    return result;
  }

  public static class ClusterConfiguration extends AbstractAmazonCredentialsDescription {
    private Map<String, Object> cluster;

    public Map<String, Object> getCluster() {
      return cluster;
    }

    public void setCluster(Map<String, Object> cluster) {
      this.cluster = cluster;
    }

    public List<String> getSecurityGroupIds() {
      return (List<String>) cluster.get("securityGroups");
    }

    public String getVpcId() {
      return (String) cluster.get("vpcId");
    }

    public String getApplication() {
      return (String) cluster.get("application");
    }

    public List<String> getLoadBalancerNames() {
      return (List<String>) cluster.get("loadBalancers");
    }

    public String getRegion() {
      return ((Map<String, List<String>>) cluster.get("availabilityZones")).keySet().iterator().next();
    }

    @Override
    public String toString() {
      AutoScalingGroupNameBuilder nameBuilder = new AutoScalingGroupNameBuilder();
      nameBuilder.setAppName(getApplication());
      nameBuilder.setStack((String) cluster.get("stack"));
      nameBuilder.setDetail((String) cluster.get("freeFormDetails"));
      return nameBuilder.buildGroupName() + " in " + cluster.get("account") + "/" + getRegion() +
        (getVpcId() != null ? "/" + getVpcId() : "");
    }
  }

  public static class ClusterConfigurationTarget extends AbstractAmazonCredentialsDescription {
    private String region;
    private String vpcId;
    private List<String> availabilityZones;

    public String getRegion() {
      return region;
    }

    public void setRegion(String region) {
      this.region = region;
    }

    public String getVpcId() {
      return vpcId;
    }

    public void setVpcId(String vpcId) {
      this.vpcId = vpcId;
    }

    public List<String> getAvailabilityZones() {
      return availabilityZones;
    }

    public void setAvailabilityZones(List<String> availabilityZones) {
      this.availabilityZones = availabilityZones;
    }

    @Override
    public String toString() {
      return getCredentialAccount() + "/" + region + (vpcId != null ? "/" + vpcId : "");
    }
  }
}
