/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.provider.view;

import com.amazonaws.services.applicationautoscaling.model.ScalableTarget;
import com.amazonaws.services.ec2.model.GroupIdentifier;
import com.amazonaws.services.ecs.model.ContainerDefinition;
import com.amazonaws.services.ecs.model.NetworkInterface;
import com.google.common.collect.Sets;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.EcsCloudWatchAlarmCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.EcsLoadbalancerCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ScalableTargetCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ServiceCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.TaskCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.TaskDefinitionCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.EcsMetricAlarm;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Service;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Task;
import com.netflix.spinnaker.clouddriver.ecs.model.EcsServerCluster;
import com.netflix.spinnaker.clouddriver.ecs.model.EcsServerGroup;
import com.netflix.spinnaker.clouddriver.ecs.model.EcsTask;
import com.netflix.spinnaker.clouddriver.ecs.model.TaskDefinition;
import com.netflix.spinnaker.clouddriver.ecs.services.ContainerInformationService;
import com.netflix.spinnaker.clouddriver.ecs.services.SubnetSelector;
import com.netflix.spinnaker.clouddriver.model.ClusterProvider;
import com.netflix.spinnaker.clouddriver.model.Instance;
import com.netflix.spinnaker.clouddriver.model.LoadBalancer;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class EcsServerClusterProvider implements ClusterProvider<EcsServerCluster> {

  private final TaskCacheClient taskCacheClient;
  private final ServiceCacheClient serviceCacheClient;
  private final ScalableTargetCacheClient scalableTargetCacheClient;
  private final TaskDefinitionCacheClient taskDefinitionCacheClient;
  private final EcsLoadbalancerCacheClient ecsLoadbalancerCacheClient;
  private final EcsCloudWatchAlarmCacheClient ecsCloudWatchAlarmCacheClient;
  private final AccountCredentialsProvider accountCredentialsProvider;
  private final ContainerInformationService containerInformationService;
  private final SubnetSelector subnetSelector;

  private final Logger log = LoggerFactory.getLogger(getClass());

  @Autowired
  public EcsServerClusterProvider(AccountCredentialsProvider accountCredentialsProvider,
                                  ContainerInformationService containerInformationService,
                                  SubnetSelector subnetSelector,
                                  TaskCacheClient taskCacheClient,
                                  ServiceCacheClient serviceCacheClient,
                                  ScalableTargetCacheClient scalableTargetCacheClient,
                                  EcsLoadbalancerCacheClient ecsLoadbalancerCacheClient,
                                  TaskDefinitionCacheClient taskDefinitionCacheClient,
                                  EcsCloudWatchAlarmCacheClient ecsCloudWatchAlarmCacheClient) {
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.containerInformationService = containerInformationService;
    this.subnetSelector = subnetSelector;
    this.taskCacheClient = taskCacheClient;
    this.serviceCacheClient = serviceCacheClient;
    this.scalableTargetCacheClient = scalableTargetCacheClient;
    this.taskDefinitionCacheClient = taskDefinitionCacheClient;
    this.ecsLoadbalancerCacheClient = ecsLoadbalancerCacheClient;
    this.ecsCloudWatchAlarmCacheClient = ecsCloudWatchAlarmCacheClient;
  }

  private Map<String, Set<EcsServerCluster>> findClusters(Map<String, Set<EcsServerCluster>> clusterMap,
                                                          AmazonCredentials credentials) {
    return findClusters(clusterMap, credentials, null);
  }

  private Map<String, Set<EcsServerCluster>> findClusters(Map<String, Set<EcsServerCluster>> clusterMap,
                                                          AmazonCredentials credentials,
                                                          String application) {
    for (AmazonCredentials.AWSRegion awsRegion : credentials.getRegions()) {
      clusterMap = findClustersForRegion(clusterMap, credentials, awsRegion, application);
    }

    return clusterMap;
  }

  private Map<String, Set<EcsServerCluster>> findClustersForRegion(Map<String, Set<EcsServerCluster>> clusterMap,
                                                                   AmazonCredentials credentials,
                                                                   AmazonCredentials.AWSRegion awsRegion,
                                                                   String application) {

    Collection<Service> services = serviceCacheClient.getAll(credentials.getName(), awsRegion.getName());
    Collection<Task> allTasks = taskCacheClient.getAll(credentials.getName(), awsRegion.getName());

    for (Service service : services) {
      String applicationName = service.getApplicationName();
      String serviceName = service.getServiceName();

      if (application != null && !applicationName.equals(application)) {
        continue;
      }

      Set<LoadBalancer> loadBalancers = new HashSet<>(ecsLoadbalancerCacheClient.find(credentials.getName(), awsRegion.getName()));

      Set<Instance> instances = allTasks.stream()
        .filter(task -> task.getGroup().equals("service:" + serviceName))
        .map(task -> convertToEcsTask(credentials.getName(), awsRegion.getName(), serviceName, task))
        .collect(Collectors.toSet());

      String taskDefinitionKey = Keys.getTaskDefinitionKey(credentials.getName(), awsRegion.getName(), service.getTaskDefinition());
      com.amazonaws.services.ecs.model.TaskDefinition taskDefinition = taskDefinitionCacheClient.get(taskDefinitionKey);
      if (taskDefinition == null) {
        continue;
      }

      EcsServerGroup ecsServerGroup = buildEcsServerGroup(credentials.getName(), awsRegion.getName(),
        serviceName, service.getDesiredCount(), instances, service.getCreatedAt(),
        service.getClusterName(), taskDefinition, service.getSubnets(), service.getSecurityGroups());

      if (ecsServerGroup == null) {
        continue;
      }

      if (clusterMap.containsKey(applicationName)) {
        String escClusterName = StringUtils.substringBeforeLast(ecsServerGroup.getName(), "-");
        boolean found = false;

        for (EcsServerCluster cluster : clusterMap.get(applicationName)) {
          if (cluster.getName().equals(escClusterName)) {
            cluster.getServerGroups().add(ecsServerGroup);
            found = true;
            break;
          }
        }

        if (!found) {
          EcsServerCluster spinnakerCluster = buildSpinnakerServerCluster(credentials, loadBalancers, ecsServerGroup);
          clusterMap.get(applicationName).add(spinnakerCluster);
        }
      } else {
        EcsServerCluster spinnakerCluster = buildSpinnakerServerCluster(credentials, loadBalancers, ecsServerGroup);
        clusterMap.put(applicationName, Sets.newHashSet(spinnakerCluster));
      }
    }

    return clusterMap;
  }

  private EcsTask convertToEcsTask(String account, String region, String serviceName, Task task) {
    String taskId = task.getTaskId();
    Long launchTime = task.getStartedAt();

    String address = containerInformationService.getTaskPrivateAddress(account, region, task);
    List<Map<String, Object>> healthStatus = containerInformationService.getHealthStatus(taskId, serviceName, account, region);
    String availabilityZone = containerInformationService.getTaskZone(account, region, task);

    NetworkInterface networkInterface =
      !task.getContainers().isEmpty()
        && !task.getContainers().get(0).getNetworkInterfaces().isEmpty()
        ? task.getContainers().get(0).getNetworkInterfaces().get(0) : null;

    return new EcsTask(taskId, launchTime, task.getLastStatus(), task.getDesiredStatus(), availabilityZone, healthStatus, address, networkInterface);
  }

  private TaskDefinition buildTaskDefinition(com.amazonaws.services.ecs.model.TaskDefinition taskDefinition) {
    String roleArn = taskDefinition.getTaskRoleArn();
    String iamRole = roleArn != null ? StringUtils.substringAfterLast(roleArn, "/") : "None";
    ContainerDefinition containerDefinition = taskDefinition.getContainerDefinitions().get(0);

    int cpu = 0;
    if (containerDefinition.getCpu() != null) {
      cpu = containerDefinition.getCpu();
    } else if (taskDefinition.getCpu() != null) {
      cpu = Integer.parseInt(taskDefinition.getCpu());
    }

    int memoryReservation = 0;
    if (containerDefinition.getMemoryReservation() != null) {
      memoryReservation = containerDefinition.getMemoryReservation();
    }

    int memoryLimit = 0;
    if (containerDefinition.getMemory() != null) {
      memoryLimit = containerDefinition.getMemory();
    } else if (taskDefinition.getMemory() != null) {
      memoryLimit = Integer.parseInt(taskDefinition.getMemory());
    }

    return new TaskDefinition()
      .setContainerImage(containerDefinition.getImage())
      .setContainerPort(containerDefinition.getPortMappings().isEmpty() ? 0 : containerDefinition.getPortMappings().get(0).getContainerPort())
      .setCpuUnits(cpu)
      .setMemoryReservation(memoryReservation)
      .setMemoryLimit(memoryLimit)
      .setIamRole(iamRole)
      .setTaskName(StringUtils.substringAfterLast(taskDefinition.getTaskDefinitionArn(), "/"))
      .setEnvironmentVariables(containerDefinition.getEnvironment());
  }

  private ServerGroup.Capacity buildServerGroupCapacity(int desiredCount, ScalableTarget target) {
    ServerGroup.Capacity capacity = new ServerGroup.Capacity();
    capacity.setDesired(desiredCount);
    if (target != null) {
      capacity.setMin(target.getMinCapacity());
      capacity.setMax(target.getMaxCapacity());
    } else {
      //TODO: Min/Max should be based on (desired count * min/max precent).
      capacity.setMin(desiredCount);
      capacity.setMax(desiredCount);
    }
    return capacity;
  }

  private EcsServerCluster buildSpinnakerServerCluster(AmazonCredentials credentials,
                                                       Set<LoadBalancer> loadBalancers,
                                                       EcsServerGroup ecsServerGroup) {
    return new EcsServerCluster()
      .setAccountName(credentials.getName())
      .setName(StringUtils.substringBeforeLast(ecsServerGroup.getName(), "-"))
      .setLoadBalancers(loadBalancers)
      .setServerGroups(Sets.newHashSet(ecsServerGroup));
  }

  private EcsServerGroup buildEcsServerGroup(String account,
                                             String region,
                                             String serviceName,
                                             int desiredCount,
                                             Set<Instance> instances,
                                             long creationTime,
                                             String ecsCluster,
                                             com.amazonaws.services.ecs.model.TaskDefinition taskDefinition,
                                             List<String> eniSubnets,
                                             List<String> eniSecurityGroups) {
    ServerGroup.InstanceCounts instanceCounts = buildInstanceCount(instances);
    TaskDefinition ecsTaskDefinition = buildTaskDefinition(taskDefinition);

    String scalableTargetId = "service/" + ecsCluster + "/" + serviceName;
    String scalableTargetKey = Keys.getScalableTargetKey(account, region, scalableTargetId);
    ScalableTarget scalableTarget = scalableTargetCacheClient.get(scalableTargetKey);
    if (scalableTarget == null) {
      return null;
    }

    ServerGroup.Capacity capacity = buildServerGroupCapacity(desiredCount, scalableTarget);

    String vpcId = "None";
    Set<String> securityGroups = new HashSet<>();

    if (!instances.isEmpty()) {
      String taskId = instances.iterator().next().getName();
      String taskKey = Keys.getTaskKey(account, region, taskId);
      Task task = taskCacheClient.get(taskKey);

      com.amazonaws.services.ec2.model.Instance ec2Instance = containerInformationService.getEc2Instance(account, region, task);

      if (eniSubnets != null && !eniSubnets.isEmpty() && eniSecurityGroups != null && !eniSecurityGroups.isEmpty()) {
        securityGroups = eniSecurityGroups.stream().collect(Collectors.toSet());

        Collection<String> vpcIds = subnetSelector.getSubnetVpcIds(account, region, eniSubnets);

        if (!vpcIds.isEmpty()) {
          if (vpcIds.size() > 1) {
            throw new IllegalArgumentException("Services with multiple VPCs are not supported");
          }

          vpcId = vpcIds.iterator().next();
        }
      } else if (ec2Instance != null) {
        vpcId = ec2Instance.getVpcId();
        securityGroups = ec2Instance.getSecurityGroups().stream()
          .map(GroupIdentifier::getGroupId)
          .collect(Collectors.toSet());
      }
    }


    Set<String> metricAlarmNames = ecsCloudWatchAlarmCacheClient.getMetricAlarms(serviceName, account, region).stream()
      .map(EcsMetricAlarm::getAlarmName)
      .collect(Collectors.toSet());

    EcsServerGroup serverGroup = new EcsServerGroup()
      .setDisabled(capacity.getDesired() == 0)
      .setName(serviceName)
      .setCloudProvider(EcsCloudProvider.ID)
      .setType(EcsCloudProvider.ID)
      .setRegion(region)
      .setInstances(instances)
      .setCapacity(capacity)
      .setInstanceCounts(instanceCounts)
      .setCreatedTime(creationTime)
      .setEcsCluster(ecsCluster)
      .setTaskDefinition(ecsTaskDefinition)
      .setVpcId(vpcId)
      .setSecurityGroups(securityGroups)
      .setMetricAlarms(metricAlarmNames);

    EcsServerGroup.AutoScalingGroup asg = new EcsServerGroup.AutoScalingGroup()
      .setDesiredCapacity(scalableTarget.getMaxCapacity())
      .setMaxSize(scalableTarget.getMaxCapacity())
      .setMinSize(scalableTarget.getMinCapacity());

    // TODO: Update Deck to handle an asg. Current Deck implementation uses a EC2 AutoScaling Group
    //serverGroup.setAsg(asg);

    return serverGroup;
  }

  private ServerGroup.InstanceCounts buildInstanceCount(Set<Instance> instances) {
    ServerGroup.InstanceCounts instanceCounts = new ServerGroup.InstanceCounts();
    for (Instance instance : instances) {
      switch (instance.getHealthState()) {
        case Up:
          instanceCounts.setUp(instanceCounts.getUp() + 1);
          break;
        case Down:
          instanceCounts.setDown(instanceCounts.getDown() + 1);
          break;
        case Failed:
          instanceCounts.setDown(instanceCounts.getDown() + 1);
          break;
        case Starting:
          instanceCounts.setOutOfService(instanceCounts.getOutOfService() + 1);
          break;
        case Unknown:
          instanceCounts.setUnknown(instanceCounts.getUnknown() + 1);
          break;
        case OutOfService:
          instanceCounts.setOutOfService(instanceCounts.getOutOfService() + 1);
          break;
        case Succeeded:
          instanceCounts.setUp(instanceCounts.getUp());
          break;
        default:
          throw new Error(String.format(
            "Unexpected health state: %s.  Don't know how to proceed - update %s",
            instance.getHealthState(),
            this.getClass().getSimpleName()));
      }
      instanceCounts.setTotal(instanceCounts.getTotal() + 1);
    }
    return instanceCounts;
  }

  private List<AmazonCredentials> getEcsCredentials() {
    List<AmazonCredentials> ecsCredentialsList = new ArrayList<>();
    for (AccountCredentials credentials : accountCredentialsProvider.getAll()) {
      if (credentials instanceof AmazonCredentials && credentials.getCloudProvider().equals(EcsCloudProvider.ID)) {
        ecsCredentialsList.add((AmazonCredentials) credentials);
      }
    }
    return ecsCredentialsList;
  }

  private AmazonCredentials getEcsCredentials(String account) {
    try {
      return getEcsCredentials().stream()
        .filter(credentials -> credentials.getName().equals(account))
        .findFirst().get();
    } catch (NoSuchElementException exception) {
      throw new NoSuchElementException(String.format("There is no ECS account by the name of '%s'", account));
    }
  }

  @Override
  public Map<String, Set<EcsServerCluster>> getClusterSummaries(String application) {
    return getClusters();
  }

  @Override
  public Map<String, Set<EcsServerCluster>> getClusterDetails(String application) {
    Map<String, Set<EcsServerCluster>> clusterMap = new HashMap<>();

    for (AmazonCredentials credentials : getEcsCredentials()) {
      clusterMap = findClusters(clusterMap, credentials, application);
    }

    return clusterMap;
  }


  @Override
  public Map<String, Set<EcsServerCluster>> getClusters() {
    Map<String, Set<EcsServerCluster>> clusterMap = new HashMap<>();

    for (AmazonCredentials credentials : getEcsCredentials()) {
      clusterMap = findClusters(clusterMap, credentials);
    }
    return clusterMap;
  }

  /**
   * Gets Spinnaker clusters for a given Spinnaker application and ECS account.
   */
  @Override
  public Set<EcsServerCluster> getClusters(String application, String account) {
    try {
      AmazonCredentials credentials = getEcsCredentials(account);
      return findClusters(new HashMap<>(), credentials, application)
        .get(application);
    } catch (NoSuchElementException exception) {
      log.info("No ECS Credentials were found for account " + account);
      return null;
    }

  }


  /**
   * Gets a Spinnaker clusters for a given Spinnaker application, ECS account, and the Spinnaker cluster name.
   */
  @Override
  public EcsServerCluster getCluster(String application, String account, String name) {
    Set<EcsServerCluster> ecsServerClusters = getClusters(application, account);
    if (ecsServerClusters != null && ecsServerClusters.size() > 0) {
      for (EcsServerCluster cluster : ecsServerClusters) {
        if (cluster.getName().equals(name)) {
          return cluster;
        }
      }
    }
    return null;
  }

  /**
   * Gets a Spinnaker clusters for a given Spinnaker application, ECS account, and the Spinnaker cluster name.
   * TODO: Make includeDetails actually function.
   */
  @Override
  public EcsServerCluster getCluster(String application, String account, String name, boolean includeDetails) {
    return getCluster(application, account, name);
  }

  /**
   * Gets a Spinnaker server group for a given Spinnaker application, ECS account, and the Spinnaker server group name.
   */
  @Override
  public ServerGroup getServerGroup(String account, String region, String serverGroupName, boolean includeDetails) {
    if (serverGroupName == null) {
      throw new Error("Invalid Server Group");
    }
    // TODO - remove the application filter.
    String application = StringUtils.substringBefore(serverGroupName, "-");
    Map<String, Set<EcsServerCluster>> clusterMap = new HashMap<>();

    try {
      AmazonCredentials credentials = getEcsCredentials(account);
      clusterMap = findClusters(clusterMap, credentials, application);
    } catch (NoSuchElementException exception) {
      /* This is ugly, but not sure how else to do it. If we don't have creds due
      *  to not being an ECS account, there's nothing to do here, and we should
      *  just continue on.
      */
      log.info("No ECS credentials were found for the account " + account);
    }

    for (Map.Entry<String, Set<EcsServerCluster>> entry : clusterMap.entrySet()) {
      for (EcsServerCluster ecsServerCluster : entry.getValue()) {
        for (ServerGroup serverGroup : ecsServerCluster.getServerGroups()) {
          if (region.equals(serverGroup.getRegion())
            && serverGroupName.equals(serverGroup.getName())) {
            return serverGroup;
          }
        }
      }
    }

    // I don't think this should throw an error.. other classes (such as the AmazonClusterProvider return null
    // if it isn't found..)
    log.info("No ECS Server Groups were found with the name " + serverGroupName);
    return null;
  }

  public ServerGroup getServerGroup(String account, String region, String serverGroupName) {
    return getServerGroup(account, region, serverGroupName, true);
  }

  @Override
  public String getCloudProviderId() {
    return EcsCloudProvider.ID;
  }

  @Override
  public boolean supportsMinimalClusters() {
    //TODO: Implement if needed.
    return false;
  }
}
