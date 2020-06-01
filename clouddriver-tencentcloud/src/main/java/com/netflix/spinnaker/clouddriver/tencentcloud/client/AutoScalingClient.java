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

package com.netflix.spinnaker.clouddriver.tencentcloud.client;

import static java.lang.Thread.sleep;

import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description.ResizeTencentCloudServerGroupDescription.Capacity;
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description.TencentCloudDeployDescription;
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description.UpsertTencentCloudScalingPolicyDescription;
import com.netflix.spinnaker.clouddriver.tencentcloud.deploy.description.UpsertTencentCloudScheduledActionDescription;
import com.netflix.spinnaker.clouddriver.tencentcloud.exception.TencentCloudOperationException;
import com.tencentcloudapi.as.v20180419.AsClient;
import com.tencentcloudapi.as.v20180419.models.Activity;
import com.tencentcloudapi.as.v20180419.models.AutoScalingGroup;
import com.tencentcloudapi.as.v20180419.models.CreateAutoScalingGroupRequest;
import com.tencentcloudapi.as.v20180419.models.CreateAutoScalingGroupResponse;
import com.tencentcloudapi.as.v20180419.models.CreateLaunchConfigurationRequest;
import com.tencentcloudapi.as.v20180419.models.CreateLaunchConfigurationResponse;
import com.tencentcloudapi.as.v20180419.models.CreateScalingPolicyRequest;
import com.tencentcloudapi.as.v20180419.models.CreateScalingPolicyResponse;
import com.tencentcloudapi.as.v20180419.models.CreateScheduledActionRequest;
import com.tencentcloudapi.as.v20180419.models.CreateScheduledActionResponse;
import com.tencentcloudapi.as.v20180419.models.DataDisk;
import com.tencentcloudapi.as.v20180419.models.DeleteAutoScalingGroupRequest;
import com.tencentcloudapi.as.v20180419.models.DeleteLaunchConfigurationRequest;
import com.tencentcloudapi.as.v20180419.models.DeleteScalingPolicyRequest;
import com.tencentcloudapi.as.v20180419.models.DeleteScheduledActionRequest;
import com.tencentcloudapi.as.v20180419.models.DescribeAutoScalingActivitiesRequest;
import com.tencentcloudapi.as.v20180419.models.DescribeAutoScalingActivitiesResponse;
import com.tencentcloudapi.as.v20180419.models.DescribeAutoScalingGroupsRequest;
import com.tencentcloudapi.as.v20180419.models.DescribeAutoScalingGroupsResponse;
import com.tencentcloudapi.as.v20180419.models.DescribeAutoScalingInstancesRequest;
import com.tencentcloudapi.as.v20180419.models.DescribeAutoScalingInstancesResponse;
import com.tencentcloudapi.as.v20180419.models.DescribeLaunchConfigurationsRequest;
import com.tencentcloudapi.as.v20180419.models.DescribeLaunchConfigurationsResponse;
import com.tencentcloudapi.as.v20180419.models.DescribeScalingPoliciesRequest;
import com.tencentcloudapi.as.v20180419.models.DescribeScalingPoliciesResponse;
import com.tencentcloudapi.as.v20180419.models.DescribeScheduledActionsRequest;
import com.tencentcloudapi.as.v20180419.models.DescribeScheduledActionsResponse;
import com.tencentcloudapi.as.v20180419.models.DisableAutoScalingGroupRequest;
import com.tencentcloudapi.as.v20180419.models.EnableAutoScalingGroupRequest;
import com.tencentcloudapi.as.v20180419.models.EnhancedService;
import com.tencentcloudapi.as.v20180419.models.Filter;
import com.tencentcloudapi.as.v20180419.models.ForwardLoadBalancer;
import com.tencentcloudapi.as.v20180419.models.Instance;
import com.tencentcloudapi.as.v20180419.models.InstanceMarketOptionsRequest;
import com.tencentcloudapi.as.v20180419.models.InstanceTag;
import com.tencentcloudapi.as.v20180419.models.InternetAccessible;
import com.tencentcloudapi.as.v20180419.models.LaunchConfiguration;
import com.tencentcloudapi.as.v20180419.models.LoginSettings;
import com.tencentcloudapi.as.v20180419.models.ModifyAutoScalingGroupRequest;
import com.tencentcloudapi.as.v20180419.models.ModifyScalingPolicyRequest;
import com.tencentcloudapi.as.v20180419.models.ModifyScheduledActionRequest;
import com.tencentcloudapi.as.v20180419.models.RemoveInstancesRequest;
import com.tencentcloudapi.as.v20180419.models.RunMonitorServiceEnabled;
import com.tencentcloudapi.as.v20180419.models.RunSecurityServiceEnabled;
import com.tencentcloudapi.as.v20180419.models.ScalingPolicy;
import com.tencentcloudapi.as.v20180419.models.ScheduledAction;
import com.tencentcloudapi.as.v20180419.models.SpotMarketOptions;
import com.tencentcloudapi.as.v20180419.models.SystemDisk;
import com.tencentcloudapi.clb.v20180317.ClbClient;
import com.tencentcloudapi.clb.v20180317.models.ClassicalTarget;
import com.tencentcloudapi.clb.v20180317.models.ClassicalTargetInfo;
import com.tencentcloudapi.clb.v20180317.models.DeregisterTargetsFromClassicalLBRequest;
import com.tencentcloudapi.clb.v20180317.models.DeregisterTargetsRequest;
import com.tencentcloudapi.clb.v20180317.models.DescribeClassicalLBTargetsRequest;
import com.tencentcloudapi.clb.v20180317.models.DescribeClassicalLBTargetsResponse;
import com.tencentcloudapi.clb.v20180317.models.DescribeTargetsRequest;
import com.tencentcloudapi.clb.v20180317.models.DescribeTargetsResponse;
import com.tencentcloudapi.clb.v20180317.models.ListenerBackend;
import com.tencentcloudapi.clb.v20180317.models.RegisterTargetsRequest;
import com.tencentcloudapi.clb.v20180317.models.RegisterTargetsWithClassicalLBRequest;
import com.tencentcloudapi.clb.v20180317.models.Target;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Component
@Slf4j
public class AutoScalingClient extends AbstractTencentCloudServiceClient {

  private static final String END_POINT = "as.tencentcloudapi.com";
  private static final String CLB_ENDPOINT = "clb.tencentcloudapi.com";
  private static final String DEFAULT_SERVER_GROUP_TAG_KEY = "spinnaker:server-group-name";

  private final AsClient client;
  private final ClbClient clbClient;

  public AutoScalingClient(String secretId, String secretKey, String region) {
    super(secretId, secretKey);

    this.client = new AsClient(getCredential(), region, getClientProfile());

    HttpProfile clbHttpProfile = new HttpProfile();
    clbHttpProfile.setEndpoint(CLB_ENDPOINT);

    ClientProfile clbClientProfile = new ClientProfile();
    clbClientProfile.setHttpProfile(clbHttpProfile);

    this.clbClient = new ClbClient(getCredential(), region, clbClientProfile);
  }

  public String deploy(TencentCloudDeployDescription description) {
    try {
      // 1. create launch configuration
      CreateLaunchConfigurationRequest createLaunchConfigurationRequest =
          buildLaunchConfigurationRequest(description);
      CreateLaunchConfigurationResponse createLaunchConfigurationResponse =
          client.CreateLaunchConfiguration(createLaunchConfigurationRequest);
      String launchConfigurationId = createLaunchConfigurationResponse.getLaunchConfigurationId();

      try {
        // 2. create auto scaling group
        CreateAutoScalingGroupRequest createAutoScalingGroupRequest =
            buildAutoScalingGroupRequest(description, launchConfigurationId);
        CreateAutoScalingGroupResponse createAutoScalingGroupResponse =
            client.CreateAutoScalingGroup(createAutoScalingGroupRequest);
        return createAutoScalingGroupResponse.getAutoScalingGroupId();
      } catch (TencentCloudSDKException e) {
        // if create auto scaling group failed, delete launch configuration.
        log.error(e.toString());
        DeleteLaunchConfigurationRequest request = new DeleteLaunchConfigurationRequest();
        request.setLaunchConfigurationId(launchConfigurationId);
        client.DeleteLaunchConfiguration(request);
        throw e;
      }
    } catch (TencentCloudSDKException e) {
      throw new TencentCloudOperationException(e.toString());
    }
  }

  private CreateLaunchConfigurationRequest buildLaunchConfigurationRequest(
      TencentCloudDeployDescription description) {
    CreateLaunchConfigurationRequest createLaunchConfigurationRequest =
        new CreateLaunchConfigurationRequest();

    String launchConfigurationName = description.getServerGroupName();
    createLaunchConfigurationRequest.setLaunchConfigurationName(launchConfigurationName);
    createLaunchConfigurationRequest.setImageId(description.getImageId());

    if (description.getProjectId() != null) {
      createLaunchConfigurationRequest.setProjectId(description.getProjectId());
    }

    if (description.getInstanceType() != null) {
      createLaunchConfigurationRequest.setInstanceType(description.getInstanceType());
    }

    if (description.getSystemDisk() != null) {
      SystemDisk systemDisk = new SystemDisk();
      systemDisk.setDiskSize((description.getSystemDisk().getDiskSize()));
      systemDisk.setDiskType(description.getSystemDisk().getDiskType());
      createLaunchConfigurationRequest.setSystemDisk(systemDisk);
    }

    if (!CollectionUtils.isEmpty(description.getDataDisks())) {
      createLaunchConfigurationRequest.setDataDisks(
          description.getDataDisks().toArray(new DataDisk[0]));
    }

    if (description.getInternetAccessible() != null) {
      InternetAccessible internetAccessible = new InternetAccessible();
      internetAccessible.setInternetChargeType(
          description.getInternetAccessible().getInternetChargeType());
      internetAccessible.setInternetMaxBandwidthOut(
          description.getInternetAccessible().getInternetMaxBandwidthOut());
      internetAccessible.setPublicIpAssigned(
          description.getInternetAccessible().getPublicIpAssigned());
      createLaunchConfigurationRequest.setInternetAccessible(internetAccessible);
    }

    if (description.getLoginSettings() != null) {
      LoginSettings loginSettings = new LoginSettings();
      loginSettings.setKeepImageLogin(description.getLoginSettings().getKeepImageLogin());
      loginSettings.setKeyIds(description.getLoginSettings().getKeyIds());
      loginSettings.setPassword(description.getLoginSettings().getPassword());
      createLaunchConfigurationRequest.setLoginSettings(loginSettings);
    }

    if (!CollectionUtils.isEmpty(description.getSecurityGroupIds())) {
      createLaunchConfigurationRequest.setSecurityGroupIds(
          description.getSecurityGroupIds().toArray(new String[0]));
    }

    if (description.getEnhancedService() != null) {
      EnhancedService enhancedService = new EnhancedService();
      RunMonitorServiceEnabled monitorServiceEnabled =
          description.getEnhancedService().getMonitorService();
      RunSecurityServiceEnabled securityServiceEnabled =
          description.getEnhancedService().getSecurityService();
      enhancedService.setMonitorService(monitorServiceEnabled);
      enhancedService.setSecurityService(securityServiceEnabled);
      createLaunchConfigurationRequest.setEnhancedService(enhancedService);
    }

    if (!StringUtils.isEmpty(description.getUserData())) {
      createLaunchConfigurationRequest.setUserData(description.getUserData());
    }

    if (!StringUtils.isEmpty(description.getInstanceChargeType())) {
      createLaunchConfigurationRequest.setInstanceChargeType(description.getInstanceChargeType());
    }

    if (description.getInstanceMarketOptionsRequest() != null) {
      InstanceMarketOptionsRequest instanceMarketOptionsRequest =
          new InstanceMarketOptionsRequest();
      instanceMarketOptionsRequest.setMarketType(
          description.getInstanceMarketOptionsRequest().getMarketType());

      SpotMarketOptions spotOptions = new SpotMarketOptions();
      spotOptions.setMaxPrice(
          description.getInstanceMarketOptionsRequest().getSpotOptions().getMaxPrice());
      spotOptions.setSpotInstanceType(
          description.getInstanceMarketOptionsRequest().getSpotOptions().getSpotInstanceType());
      instanceMarketOptionsRequest.setSpotOptions(spotOptions);

      createLaunchConfigurationRequest.setInstanceMarketOptions(instanceMarketOptionsRequest);
    }

    if (description.getInstanceTypes() != null) {
      createLaunchConfigurationRequest.setInstanceTypes(
          description.getInstanceTypes().toArray(new String[0]));
    }

    if (!StringUtils.isEmpty(description.getInstanceTypesCheckPolicy())) {
      createLaunchConfigurationRequest.setInstanceTypesCheckPolicy(
          description.getInstanceTypesCheckPolicy());
    }

    InstanceTag spinnakerTag = new InstanceTag();
    spinnakerTag.setKey(DEFAULT_SERVER_GROUP_TAG_KEY);
    spinnakerTag.setValue(description.getServerGroupName());

    List<InstanceTag> instanceTags = new ArrayList<>(Arrays.asList(spinnakerTag));
    instanceTags.addAll(description.getInstanceTags());

    createLaunchConfigurationRequest.setInstanceTags(instanceTags.toArray(new InstanceTag[0]));

    return createLaunchConfigurationRequest;
  }

  private static CreateAutoScalingGroupRequest buildAutoScalingGroupRequest(
      TencentCloudDeployDescription description, String launchConfigurationId) {
    CreateAutoScalingGroupRequest createAutoScalingGroupRequest =
        new CreateAutoScalingGroupRequest();
    createAutoScalingGroupRequest.setAutoScalingGroupName(description.getServerGroupName());
    createAutoScalingGroupRequest.setLaunchConfigurationId(launchConfigurationId);
    createAutoScalingGroupRequest.setDesiredCapacity(description.getDesiredCapacity());
    createAutoScalingGroupRequest.setMinSize(description.getMinSize());
    createAutoScalingGroupRequest.setMaxSize(description.getMaxSize());
    createAutoScalingGroupRequest.setVpcId(description.getVpcId());

    if (!CollectionUtils.isEmpty(description.getSubnetIds())) {
      createAutoScalingGroupRequest.setSubnetIds(description.getSubnetIds().toArray(new String[0]));
    }

    if (!CollectionUtils.isEmpty(description.getZones())) {
      createAutoScalingGroupRequest.setZones(description.getZones().toArray(new String[0]));
    }

    if (description.getProjectId() != null) {
      createAutoScalingGroupRequest.setProjectId(description.getProjectId());
    }

    if (description.getRetryPolicy() != null) {
      createAutoScalingGroupRequest.setRetryPolicy(description.getRetryPolicy());
    }

    if (description.getZonesCheckPolicy() != null) {
      createAutoScalingGroupRequest.setZonesCheckPolicy(description.getZonesCheckPolicy());
    }

    if (description.getDefaultCooldown() != null) {
      createAutoScalingGroupRequest.setDefaultCooldown(description.getDefaultCooldown());
    }

    if (!CollectionUtils.isEmpty(description.getForwardLoadBalancers())) {
      createAutoScalingGroupRequest.setForwardLoadBalancers(
          description.getForwardLoadBalancers().toArray(new ForwardLoadBalancer[0]));
    }

    if (description.getLoadBalancerIds() != null) {
      createAutoScalingGroupRequest.setLoadBalancerIds(
          description.getLoadBalancerIds().toArray(new String[0]));
    }

    if (description.getTerminationPolicies() != null) {
      createAutoScalingGroupRequest.setTerminationPolicies(
          description.getTerminationPolicies().toArray(new String[0]));
    }

    return createAutoScalingGroupRequest;
  }

  public List<AutoScalingGroup> getAllAutoScalingGroups() {
    try {
      DescribeAutoScalingGroupsRequest request = new DescribeAutoScalingGroupsRequest();
      request.setLimit(DEFAULT_LIMIT);
      DescribeAutoScalingGroupsResponse response = client.DescribeAutoScalingGroups(request);
      return Arrays.asList(response.getAutoScalingGroupSet());
    } catch (TencentCloudSDKException e) {
      throw new TencentCloudOperationException(e.toString());
    }
  }

  public List<AutoScalingGroup> getAutoScalingGroupsByName(String name) {
    try {
      DescribeAutoScalingGroupsRequest request = new DescribeAutoScalingGroupsRequest();
      request.setLimit(DEFAULT_LIMIT);
      Filter filter = new Filter();
      filter.setName("auto-scaling-group-name");
      filter.setValues(new String[] {name});
      DescribeAutoScalingGroupsResponse response = client.DescribeAutoScalingGroups(request);
      return Arrays.asList(response.getAutoScalingGroupSet());
    } catch (TencentCloudSDKException e) {
      throw new TencentCloudOperationException(e.toString());
    }
  }

  public List<LaunchConfiguration> getLaunchConfigurations(List<String> launchConfigurationIds) {
    try {
      int len = launchConfigurationIds.size();
      List<LaunchConfiguration> launchConfigurations = new ArrayList<>();
      DescribeLaunchConfigurationsRequest request = new DescribeLaunchConfigurationsRequest();
      request.setLimit(DEFAULT_LIMIT);
      for (int i = 0; i < len; i += DEFAULT_LIMIT) {
        int endIndex = Math.toIntExact(Math.min(len, i + DEFAULT_LIMIT));
        request.setLaunchConfigurationIds(
            launchConfigurationIds.subList(i, endIndex).toArray(new String[0]));

        DescribeLaunchConfigurationsResponse response =
            client.DescribeLaunchConfigurations(request);
        List<LaunchConfiguration> launchConfigurationList =
            Arrays.stream(response.getLaunchConfigurationSet()).collect(Collectors.toList());
        launchConfigurations.addAll(launchConfigurationList);
      }
      return launchConfigurations;
    } catch (TencentCloudSDKException e) {
      throw new TencentCloudOperationException(e.toString());
    }
  }

  public List<Instance> getAutoScalingInstances(String asgId) {
    List<Instance> result = new ArrayList<>();
    DescribeAutoScalingInstancesRequest request = new DescribeAutoScalingInstancesRequest();

    if (!StringUtils.isEmpty(asgId)) {
      Filter filter = new Filter();
      filter.setName("auto-scaling-group-id");
      filter.setValues(new String[] {asgId});
      request.setFilters(new Filter[] {filter});
    }

    try {
      long offset = 0;
      int queryIndex = 0;
      while (queryIndex++ < MAX_QUERY_TIME) {
        request.setOffset(offset);
        request.setLimit(DEFAULT_LIMIT);
        DescribeAutoScalingInstancesResponse response =
            client.DescribeAutoScalingInstances(request);

        if (response == null
            || response.getAutoScalingInstanceSet() == null
            || response.getAutoScalingInstanceSet().length <= 0) {
          break;
        }
        result.addAll(Arrays.asList(response.getAutoScalingInstanceSet()));
        offset += DEFAULT_LIMIT;
        if (result.size() == response.getTotalCount()) {
          break;
        }
        sleep(500);
      }
    } catch (TencentCloudSDKException | InterruptedException e) {
      throw new TencentCloudOperationException(e.toString());
    }
    return result;
  }

  public List<Instance> getAutoScalingInstances() {
    return getAutoScalingInstances(null);
  }

  public List<Activity> getAutoScalingActivitiesByAsgId(String asgId, int maxActivityNum) {
    List<Activity> result = new ArrayList<>();
    DescribeAutoScalingActivitiesRequest request = new DescribeAutoScalingActivitiesRequest();

    if (!StringUtils.isEmpty(asgId)) {
      Filter filter = new Filter();
      filter.setName("auto-scaling-group-id");
      filter.setValues(new String[] {asgId});
      request.setFilters(new Filter[] {filter});
    }

    try {
      long offset = 0;
      int queryIndex = 0;
      while (queryIndex++ < MAX_QUERY_TIME) {
        request.setOffset(offset);
        request.setLimit(DEFAULT_LIMIT);

        DescribeAutoScalingActivitiesResponse response =
            client.DescribeAutoScalingActivities(request);

        if (response == null
            || response.getActivitySet() == null
            || response.getActivitySet().length <= 0
            || result.size() + response.getActivitySet().length > maxActivityNum) {
          break;
        }

        result.addAll(Arrays.asList(response.getActivitySet()));
        offset += DEFAULT_LIMIT;
        if (result.size() == response.getTotalCount()) {
          break;
        }
        sleep(500);
      }
    } catch (TencentCloudSDKException | InterruptedException e) {
      throw new TencentCloudOperationException(e.toString());
    }
    return result;
  }

  public void resizeAutoScalingGroup(String asgId, Capacity capacity) {
    try {
      ModifyAutoScalingGroupRequest request = new ModifyAutoScalingGroupRequest();
      request.setAutoScalingGroupId(asgId);
      request.setMaxSize(capacity.getMax());
      request.setMinSize(capacity.getMin());
      request.setDesiredCapacity(capacity.getDesired());

      client.ModifyAutoScalingGroup(request);
    } catch (TencentCloudSDKException e) {
      throw new TencentCloudOperationException(e.toString());
    }
  }

  public void enableAutoScalingGroup(String asgId) {
    try {
      EnableAutoScalingGroupRequest request = new EnableAutoScalingGroupRequest();
      request.setAutoScalingGroupId(asgId);
      client.EnableAutoScalingGroup(request);
    } catch (TencentCloudSDKException e) {
      throw new TencentCloudOperationException(e.toString());
    }
  }

  public void disableAutoScalingGroup(String asgId) {
    try {
      DisableAutoScalingGroupRequest request = new DisableAutoScalingGroupRequest();
      request.setAutoScalingGroupId(asgId);
      client.DisableAutoScalingGroup(request);
    } catch (TencentCloudSDKException e) {
      throw new TencentCloudOperationException(e.toString());
    }
  }

  public void deleteAutoScalingGroup(String asgId) {
    try {
      DeleteAutoScalingGroupRequest request = new DeleteAutoScalingGroupRequest();
      request.setAutoScalingGroupId(asgId);
      client.DeleteAutoScalingGroup(request);
    } catch (TencentCloudSDKException e) {
      throw new TencentCloudOperationException(e.toString());
    }
  }

  public void deleteLaunchConfiguration(String ascId) {
    try {
      DeleteLaunchConfigurationRequest request = new DeleteLaunchConfigurationRequest();
      request.setLaunchConfigurationId(ascId);
      client.DeleteLaunchConfiguration(request);
    } catch (TencentCloudSDKException e) {
      throw new TencentCloudOperationException(e.toString());
    }
  }

  public void removeInstances(String asgId, List<String> instanceIds) {
    try {
      RemoveInstancesRequest request = new RemoveInstancesRequest();
      request.setInstanceIds(instanceIds.toArray(new String[0]));
      request.setAutoScalingGroupId(asgId);
      client.RemoveInstances(request);
    } catch (TencentCloudSDKException e) {
      throw new TencentCloudOperationException(e.toString());
    }
  }

  public void attachAutoScalingInstancesToForwardClb(ForwardLoadBalancer flb, List<Target> targets)
      throws TencentCloudSDKException {
    try {
      RegisterTargetsRequest request = new RegisterTargetsRequest();
      request.setLoadBalancerId(flb.getLoadBalancerId());
      request.setListenerId(flb.getListenerId());
      request.setLocationId(flb.getLocationId());
      request.setTargets(targets.toArray(new Target[0]));

      clbClient.RegisterTargets(request);
    } catch (TencentCloudSDKException e) {
      throw new TencentCloudSDKException(e.toString());
    }
  }

  public void attachAutoScalingInstancesToClassicClb(String lbId, List<Target> targets) {
    try {
      RegisterTargetsWithClassicalLBRequest request = new RegisterTargetsWithClassicalLBRequest();
      request.setLoadBalancerId(lbId);
      List<ClassicalTargetInfo> infoList = new ArrayList<>();
      for (Target target : targets) {
        ClassicalTargetInfo info = new ClassicalTargetInfo();
        info.setInstanceId(target.getInstanceId());
        info.setWeight(target.getWeight());
        infoList.add(info);
      }
      request.setTargets(infoList.toArray(new ClassicalTargetInfo[0]));
      clbClient.RegisterTargetsWithClassicalLB(request);
    } catch (TencentCloudSDKException e) {
      throw new TencentCloudOperationException(e.toString());
    }
  }

  public void detachAutoScalingInstancesFromForwardClb(
      ForwardLoadBalancer flb, List<Target> targets) throws TencentCloudSDKException {
    try {
      DeregisterTargetsRequest request = new DeregisterTargetsRequest();
      request.setLoadBalancerId(flb.getLoadBalancerId());
      request.setListenerId(flb.getListenerId());
      request.setListenerId(flb.getListenerId());
      request.setTargets(targets.toArray(new Target[0]));
      clbClient.DeregisterTargets(request);
    } catch (TencentCloudSDKException e) {
      throw new TencentCloudSDKException(e.toString());
    }
  }

  public void detachAutoScalingInstancesFromClassicClb(String lbId, List<String> instanceIds) {
    try {
      DeregisterTargetsFromClassicalLBRequest request =
          new DeregisterTargetsFromClassicalLBRequest();
      request.setLoadBalancerId(lbId);
      request.setInstanceIds(instanceIds.toArray(new String[0]));
      clbClient.DeregisterTargetsFromClassicalLB(request);
    } catch (TencentCloudSDKException e) {
      throw new TencentCloudOperationException(e.toString());
    }
  }

  public Set<String> getClassicLbInstanceIds(String lbId) {
    try {
      DescribeClassicalLBTargetsRequest request = new DescribeClassicalLBTargetsRequest();
      request.setLoadBalancerId(lbId);
      DescribeClassicalLBTargetsResponse response = clbClient.DescribeClassicalLBTargets(request);
      return Arrays.stream(response.getTargets())
          .map(ClassicalTarget::getInstanceId)
          .collect(Collectors.toSet());
    } catch (TencentCloudSDKException e) {
      throw new TencentCloudOperationException(e.toString());
    }
  }

  public List<ListenerBackend> getForwardLbTargets(ForwardLoadBalancer flb) {
    try {
      DescribeTargetsRequest request = new DescribeTargetsRequest();
      request.setLoadBalancerId(flb.getLoadBalancerId());
      request.setListenerIds(new String[] {flb.getListenerId()});
      DescribeTargetsResponse response = clbClient.DescribeTargets(request);
      return Arrays.asList(response.getListeners());
    } catch (TencentCloudSDKException e) {
      return new ArrayList<>();
    }
  }

  public String createScalingPolicy(
      String asgId, UpsertTencentCloudScalingPolicyDescription description) {
    try {
      CreateScalingPolicyRequest request = new CreateScalingPolicyRequest();
      request.setAutoScalingGroupId(asgId);
      request.setScalingPolicyName(
          description.getServerGroupName() + "-asp-" + new Date().getTime());
      request.setAdjustmentType(description.getAdjustmentType());
      request.setAdjustmentValue(description.getAdjustmentValue());
      request.setMetricAlarm(description.getMetricAlarm());
      request.setCooldown(description.getCooldown());
      if (!CollectionUtils.isEmpty(description.getNotificationUserGroupIds())) {
        request.setNotificationUserGroupIds(
            description.getNotificationUserGroupIds().toArray(new String[0]));
      }

      CreateScalingPolicyResponse response = client.CreateScalingPolicy(request);
      return response.getAutoScalingPolicyId();
    } catch (TencentCloudSDKException e) {
      throw new TencentCloudOperationException(e.toString());
    }
  }

  public void modifyScalingPolicy(
      String aspId, UpsertTencentCloudScalingPolicyDescription description) {
    try {
      ModifyScalingPolicyRequest request = new ModifyScalingPolicyRequest();
      request.setAutoScalingPolicyId(aspId);
      request.setAdjustmentType(description.getAdjustmentType());
      request.setAdjustmentValue(description.getAdjustmentValue());
      request.setMetricAlarm(description.getMetricAlarm());
      request.setCooldown(description.getCooldown());
      if (!CollectionUtils.isEmpty(description.getNotificationUserGroupIds())) {
        request.setNotificationUserGroupIds(
            description.getNotificationUserGroupIds().toArray(new String[0]));
      }

      client.ModifyScalingPolicy(request);
    } catch (TencentCloudSDKException e) {
      throw new TencentCloudOperationException(e.toString());
    }
  }

  public List<ScalingPolicy> getScalingPolicies(String asgId) {
    List<ScalingPolicy> result = new ArrayList<>();
    DescribeScalingPoliciesRequest request = new DescribeScalingPoliciesRequest();

    if (!StringUtils.isEmpty(asgId)) {
      Filter filter = new Filter();
      filter.setName("auto-scaling-group-id");
      filter.setValues(new String[] {asgId});
      request.setFilters(new Filter[] {filter});
    }

    try {
      long offset = 0;
      int queryIndex = 0;
      while (queryIndex++ < MAX_QUERY_TIME) {
        request.setOffset(offset);
        request.setLimit(DEFAULT_LIMIT);
        DescribeScalingPoliciesResponse response = client.DescribeScalingPolicies(request);

        if (response == null
            || response.getScalingPolicySet() == null
            || response.getScalingPolicySet().length <= 0) {
          break;
        }
        result.addAll(Arrays.asList(response.getScalingPolicySet()));
        offset += DEFAULT_LIMIT;
        if (result.size() == response.getTotalCount()) {
          break;
        }
        sleep(500);
      }
    } catch (TencentCloudSDKException | InterruptedException e) {
      throw new TencentCloudOperationException(e.toString());
    }
    return result;
  }

  public void deleteScalingPolicy(String aspId) {
    try {
      DeleteScalingPolicyRequest request = new DeleteScalingPolicyRequest();
      request.setAutoScalingPolicyId(aspId);
      client.DeleteScalingPolicy(request);
    } catch (TencentCloudSDKException e) {
      throw new TencentCloudOperationException(e.toString());
    }
  }

  public String createScheduledAction(
      String asgId, UpsertTencentCloudScheduledActionDescription description) {
    try {
      CreateScheduledActionRequest request = new CreateScheduledActionRequest();
      request.setAutoScalingGroupId(asgId);
      request.setScheduledActionName(
          description.getServerGroupName() + "-asst-" + new Date().getTime());
      request.setMaxSize(description.getMaxSize());
      request.setMinSize(description.getMinSize());
      request.setDesiredCapacity(description.getDesiredCapacity());
      request.setStartTime(description.getStartTime());
      request.setEndTime(description.getEndTime());
      request.setRecurrence(description.getRecurrence());
      CreateScheduledActionResponse response = client.CreateScheduledAction(request);
      return response.getScheduledActionId();
    } catch (TencentCloudSDKException e) {
      throw new TencentCloudOperationException(e.toString());
    }
  }

  public void modifyScheduledAction(
      String asstId, UpsertTencentCloudScheduledActionDescription description) {
    try {
      ModifyScheduledActionRequest request = new ModifyScheduledActionRequest();
      request.setScheduledActionId(asstId);
      request.setMaxSize(description.getMaxSize());
      request.setMinSize(description.getMinSize());
      request.setDesiredCapacity(description.getDesiredCapacity());
      request.setStartTime(description.getStartTime());
      request.setEndTime(description.getEndTime());
      request.setRecurrence(description.getRecurrence());

      client.ModifyScheduledAction(request);
    } catch (TencentCloudSDKException e) {
      throw new TencentCloudOperationException(e.toString());
    }
  }

  public List<ScheduledAction> getScheduledAction(String asgId) {
    List<ScheduledAction> result = new ArrayList<>();
    DescribeScheduledActionsRequest request = new DescribeScheduledActionsRequest();

    if (!StringUtils.isEmpty(asgId)) {
      Filter filter = new Filter();
      filter.setName("auto-scaling-group-id");
      filter.setValues(new String[] {asgId});
      request.setFilters(new Filter[] {filter});
    }

    try {
      long offset = 0;
      int queryIndex = 0;
      while (queryIndex++ < MAX_QUERY_TIME) {
        request.setOffset(offset);
        request.setLimit(DEFAULT_LIMIT);
        DescribeScheduledActionsResponse response = client.DescribeScheduledActions(request);

        if (response == null
            || response.getScheduledActionSet() == null
            || response.getScheduledActionSet().length <= 0) {
          break;
        }
        result.addAll(Arrays.asList(response.getScheduledActionSet()));
        offset += DEFAULT_LIMIT;
        if (result.size() == response.getTotalCount()) {
          break;
        }
        sleep(500);
      }
    } catch (TencentCloudSDKException | InterruptedException e) {
      throw new TencentCloudOperationException(e.toString());
    }
    return result;
  }

  public void deleteScheduledAction(String asstId) {
    try {
      DeleteScheduledActionRequest request = new DeleteScheduledActionRequest();
      request.setScheduledActionId(asstId);
      client.DeleteScheduledAction(request);
    } catch (TencentCloudSDKException e) {
      throw new TencentCloudOperationException(e.toString());
    }
  }

  public final String getEndPoint() {
    return END_POINT;
  }

  public static String getDefaultServerGroupTagKey() {
    return DEFAULT_SERVER_GROUP_TAG_KEY;
  }
}
