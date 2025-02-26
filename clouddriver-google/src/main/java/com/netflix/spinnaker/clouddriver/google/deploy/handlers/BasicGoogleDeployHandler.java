/*
 * Copyright 2024 Harness, Inc.
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

package com.netflix.spinnaker.clouddriver.google.deploy.handlers;

import static com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.AttachedDisk;
import com.google.api.services.compute.model.Autoscaler;
import com.google.api.services.compute.model.Backend;
import com.google.api.services.compute.model.BackendService;
import com.google.api.services.compute.model.DistributionPolicy;
import com.google.api.services.compute.model.DistributionPolicyZoneConfiguration;
import com.google.api.services.compute.model.FixedOrPercent;
import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.InstanceGroupManager;
import com.google.api.services.compute.model.InstanceGroupManagerAutoHealingPolicy;
import com.google.api.services.compute.model.InstanceProperties;
import com.google.api.services.compute.model.InstanceTemplate;
import com.google.api.services.compute.model.Metadata;
import com.google.api.services.compute.model.NamedPort;
import com.google.api.services.compute.model.NetworkInterface;
import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.model.Scheduling;
import com.google.api.services.compute.model.ServiceAccount;
import com.google.api.services.compute.model.Tags;
import com.netflix.frigga.Names;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.deploy.DeployDescription;
import com.netflix.spinnaker.clouddriver.deploy.DeployHandler;
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult;
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider;
import com.netflix.spinnaker.clouddriver.google.GoogleExecutorTraits;
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties;
import com.netflix.spinnaker.clouddriver.google.deploy.GCEServerGroupNameResolver;
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil;
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller;
import com.netflix.spinnaker.clouddriver.google.deploy.SafeRetry;
import com.netflix.spinnaker.clouddriver.google.deploy.description.BasicGoogleDeployDescription;
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleOperationException;
import com.netflix.spinnaker.clouddriver.google.deploy.ops.GoogleUserDataProvider;
import com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck;
import com.netflix.spinnaker.clouddriver.google.model.GoogleLabeledResource;
import com.netflix.spinnaker.clouddriver.google.model.GoogleNetwork;
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup;
import com.netflix.spinnaker.clouddriver.google.model.GoogleSubnet;
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleBackendService;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleHttpLoadBalancingPolicy;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleInternalHttpLoadBalancer;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleInternalLoadBalancer;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancerType;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancerView;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancingPolicy;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleNetworkLoadBalancer;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleSslLoadBalancer;
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleTcpLoadBalancer;
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider;
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleLoadBalancerProvider;
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleNetworkProvider;
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleSubnetProvider;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.config.GoogleConfiguration;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.moniker.Namer;
import groovy.lang.Closure;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Log4j2
public class BasicGoogleDeployHandler
    implements DeployHandler<BasicGoogleDeployDescription>, GoogleExecutorTraits {

  private static final String BASE_PHASE = "DEPLOY";
  private static final String DEFAULT_NETWORK_NAME = "default";
  private static final String ACCESS_CONFIG_NAME = "External NAT";
  private static final String ACCESS_CONFIG_TYPE = "ONE_TO_ONE_NAT";
  private static final Integer MAX_NAME_SIZE = 64;
  private static final Integer TEMPLATE_UUID_SIZE = 8;

  @Autowired private GoogleConfigurationProperties googleConfigurationProperties;

  @Autowired private GoogleClusterProvider googleClusterProvider;

  @Autowired private GoogleConfiguration.DeployDefaults googleDeployDefaults;

  @Autowired private GoogleOperationPoller googleOperationPoller;

  @Autowired private GoogleUserDataProvider googleUserDataProvider;

  @Autowired GoogleLoadBalancerProvider googleLoadBalancerProvider;

  @Autowired GoogleNetworkProvider googleNetworkProvider;

  @Autowired GoogleSubnetProvider googleSubnetProvider;

  @Autowired String clouddriverUserAgentApplicationName;

  @Autowired Cache cacheView;

  @Autowired ObjectMapper objectMapper;

  @Autowired SafeRetry safeRetry;

  @Autowired Registry registry;

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public DeploymentResult handle(BasicGoogleDeployDescription description, List priorOutputs) {
    Task task = getTask();

    try {
      String region = getRegionFromInput(description);
      String location = getLocationFromInput(description, region);
      GCEServerGroupNameResolver nameResolver = getServerGroupNameResolver(description, region);
      String clusterName =
          nameResolver.getClusterName(
              description.getApplication(),
              description.getStack(),
              description.getFreeFormDetails());

      task.updateStatus(
          BASE_PHASE,
          String.format(
              "Initializing creation of server group for cluster %s in %s...",
              clusterName, location));
      task.updateStatus(BASE_PHASE, "Looking up next sequence...");

      String nextServerGroupName =
          nameResolver.resolveNextServerGroupName(
              description.getApplication(),
              description.getStack(),
              description.getFreeFormDetails(),
              false);
      task.updateStatus(
          BASE_PHASE, String.format("Produced server group name: %s", nextServerGroupName));

      String machineTypeName = getMachineTypeNameFromInput(description, task, location);
      GoogleNetwork network = buildNetworkFromInput(description, task);
      GoogleSubnet subnet = buildSubnetFromInput(description, task, network, region);
      LoadBalancerInfo lbToUpdate = getLoadBalancerToUpdateFromInput(description, task);

      task.updateStatus(
          BASE_PHASE, String.format("Composing server group %s...", nextServerGroupName));

      description.setBaseDeviceName(
          nextServerGroupName); // I left this because I assumed GCEUtils use it at some point.
      Image bootImage = buildBootImage(description, task);
      List<AttachedDisk> attachedDisks = buildAttachedDisks(description, task, bootImage);
      NetworkInterface networkInterface = buildNetworkInterface(description, network, subnet);
      GoogleHttpLoadBalancingPolicy lbPolicy = null;
      List<BackendService> backendServicesToUpdate =
          getBackendServiceToUpdate(description, nextServerGroupName, lbToUpdate, lbPolicy, region);
      List<BackendService> regionBackendServicesToUpdate =
          getRegionBackendServicesToUpdate(
              description, nextServerGroupName, lbToUpdate, lbPolicy, region);

      String now = String.valueOf(System.currentTimeMillis());
      String suffix = now.substring(now.length() - TEMPLATE_UUID_SIZE);
      String instanceTemplateName = String.format("%s-%s", nextServerGroupName, suffix);
      if (instanceTemplateName.length() > MAX_NAME_SIZE) {
        throw new IllegalArgumentException(
            "Max name length ${MAX_NAME_SIZE} exceeded in resolved instance template name ${instanceTemplateName}.");
      }

      addUserDataToInstanceMetadata(description, nextServerGroupName, instanceTemplateName, task);
      addSelectZonesToInstanceMetadata(description);

      Metadata metadata = buildMetadataFromInstanceMetadata(description);
      Tags tags = buildTagsFromInput(description);
      List<ServiceAccount> serviceAccounts = buildServiceAccountFromInput(description);
      Scheduling scheduling = buildSchedulingFromInput(description);
      Map<String, String> labels = buildLabelsFromInput(description, nextServerGroupName, region);

      setupMonikerForOperation(description, nextServerGroupName, clusterName);
      validateAcceleratorConfig(description);

      InstanceProperties instanceProperties =
          buildInstancePropertiesFromInput(
              description,
              machineTypeName,
              attachedDisks,
              networkInterface,
              metadata,
              tags,
              serviceAccounts,
              scheduling,
              labels);
      addShieldedVmConfigToInstanceProperties(description, instanceProperties, bootImage);
      addMinCpuPlatformToInstanceProperties(description, instanceProperties);
      InstanceTemplate instanceTemplate =
          buildInstanceTemplate(instanceTemplateName, instanceProperties);

      String instanceTemplateUrl = "";
      instanceTemplateUrl =
          createInstanceTemplateAndWait(description.getCredentials(), instanceTemplate, task);

      setCapacityFromInput(description);
      setAutoscalerCapacityFromInput(description);
      setCapacityFromSource(description, task);

      List<InstanceGroupManagerAutoHealingPolicy> autoHealingPolicy =
          buildAutoHealingPolicyFromInput(description, task);
      InstanceGroupManager instanceGroupManager =
          buildInstanceGroupFromInput(
              description,
              nextServerGroupName,
              instanceTemplateUrl,
              lbToUpdate.targetPools,
              autoHealingPolicy);
      setNamedPortsToInstanceGroup(description, lbToUpdate, instanceGroupManager);

      createInstanceGroupManagerFromInput(
          description, instanceGroupManager, lbToUpdate, nextServerGroupName, region, task);

      task.updateStatus(
          BASE_PHASE,
          String.format("Done creating server group %s in %s.", nextServerGroupName, location));

      updateBackendServices(
          description, lbToUpdate, nextServerGroupName, backendServicesToUpdate, task);
      updateRegionalBackendServices(
          description,
          lbToUpdate,
          nextServerGroupName,
          region,
          regionBackendServicesToUpdate,
          task);

      DeploymentResult deploymentResult = new DeploymentResult();
      deploymentResult.setServerGroupNames(
          List.of(String.format("%s:%s", region, nextServerGroupName)));
      deploymentResult.setServerGroupNameByRegion(Map.of(region, nextServerGroupName));
      return deploymentResult;

    } catch (IOException e) {
      throw new IllegalStateException("Unexpected error in handler: " + e.getMessage());
    }
  }

  protected GCEServerGroupNameResolver getServerGroupNameResolver(
      BasicGoogleDeployDescription description, String region) {
    GoogleNamedAccountCredentials credentials = description.getCredentials();
    return new GCEServerGroupNameResolver(
        credentials.getProject(), region, credentials, googleClusterProvider, safeRetry, this);
  }

  protected String getRegionFromInput(BasicGoogleDeployDescription description) {
    return StringUtils.isNotBlank(description.getRegion())
        ? description.getRegion()
        : description.getCredentials().regionFromZone(description.getZone());
  }

  protected String getLocationFromInput(BasicGoogleDeployDescription description, String region) {
    return description.getRegional() ? region : description.getZone();
  }

  protected String getMachineTypeNameFromInput(
      BasicGoogleDeployDescription description, Task task, String location) {
    if (description.getInstanceType().contains("custom-")) {
      return description.getInstanceType();
    } else {
      List<String> queryZone =
          description.getRegional()
              ? (Optional.ofNullable(description.getSelectZones()).orElse(false)
                  ? Optional.ofNullable(description.getDistributionPolicy().getZones())
                      .orElse(Collections.singletonList(location))
                  : Collections.singletonList(location))
              : Collections.singletonList(location);
      String machineTypeName = "";
      for (String zoneOrLocation : queryZone) {
        String msg =
            description.getRegional()
                ? (description.getSelectZones()
                    ? "Machine type "
                        + description.getInstanceType()
                        + " not found in zone "
                        + zoneOrLocation
                        + ". When using selectZones, the machine type must be available in all selected zones."
                    : "Machine type "
                        + description.getInstanceType()
                        + " not found in zone "
                        + zoneOrLocation
                        + ". When using Regional distribution without explicit selection of Zones, the machine type must be available in all zones of the region.")
                : "Machine type "
                    + description.getInstanceType()
                    + " not found in region "
                    + zoneOrLocation
                    + ".";
        try {
          machineTypeName =
              GCEUtil.queryMachineType(
                  description.getInstanceType(),
                  zoneOrLocation,
                  description.getCredentials(),
                  task,
                  BASE_PHASE);
        } catch (GoogleOperationException e) {
          throw new GoogleOperationException(msg);
        }
      }
      return machineTypeName;
    }
  }

  protected GoogleNetwork buildNetworkFromInput(
      BasicGoogleDeployDescription description, Task task) {
    String networkName =
        StringUtils.isNotBlank(description.getNetwork())
            ? description.getNetwork()
            : DEFAULT_NETWORK_NAME;
    return GCEUtil.queryNetwork(
        description.getAccountName(), networkName, task, BASE_PHASE, googleNetworkProvider);
  }

  protected GoogleSubnet buildSubnetFromInput(
      BasicGoogleDeployDescription description, Task task, GoogleNetwork network, String region) {
    GoogleSubnet subnet =
        StringUtils.isNotBlank(description.getSubnet())
            ? GCEUtil.querySubnet(
                description.getAccountName(),
                region,
                description.getSubnet(),
                task,
                BASE_PHASE,
                googleSubnetProvider)
            : null;

    // If no subnet is passed and the network is both an xpn host network and an auto-subnet
    // network, then we need to set the subnet ourselves here.
    // This shouldn't be required, but GCE complains otherwise.
    if (subnet != null && network.getId().contains("/") && network.getAutoCreateSubnets()) {
      // Auto-created subnets have the same name as the containing network.
      subnet =
          GCEUtil.querySubnet(
              description.getAccountName(),
              region,
              network.getId(),
              task,
              BASE_PHASE,
              googleSubnetProvider);
    }
    return subnet;
  }

  protected LoadBalancerInfo getLoadBalancerToUpdateFromInput(
      BasicGoogleDeployDescription description, Task task) {
    // We need the full url for each referenced network load balancer, and also to check that the
    // HTTP(S)
    // load balancers exist.
    LoadBalancerInfo info = new LoadBalancerInfo();
    if (description.getLoadBalancers().isEmpty()) {
      return info;
    }
    // GCEUtil.queryAllLoadBalancers() will throw an exception if a referenced load balancer cannot
    // be resolved.
    List<GoogleLoadBalancerView> foundLB =
        GCEUtil.queryAllLoadBalancers(
            googleLoadBalancerProvider, description.getLoadBalancers(), task, BASE_PHASE);
    // Queue ILBs to update, but wait to update metadata until Https LBs are calculated.
    info.internalLoadBalancers =
        foundLB.stream()
            .filter(lb -> lb.getLoadBalancerType() == GoogleLoadBalancerType.INTERNAL)
            .collect(Collectors.toList());
    info.internalHttpLoadBalancers =
        foundLB.stream()
            .filter(lb -> lb.getLoadBalancerType() == GoogleLoadBalancerType.INTERNAL_MANAGED)
            .collect(Collectors.toList());
    // Queue SSL LBs to update.
    info.sslLoadBalancers =
        foundLB.stream()
            .filter(lb -> lb.getLoadBalancerType() == GoogleLoadBalancerType.SSL)
            .collect(Collectors.toList());
    // Queue TCP LBs to update.
    info.tcpLoadBalancers =
        foundLB.stream()
            .filter(lb -> lb.getLoadBalancerType() == GoogleLoadBalancerType.TCP)
            .collect(Collectors.toList());

    if (!description.getDisableTraffic()) {
      info.targetPools =
          foundLB.stream()
              .filter(lb -> lb.getLoadBalancerType() == GoogleLoadBalancerType.NETWORK)
              .map(lb -> (GoogleNetworkLoadBalancer.View) lb)
              .map(GoogleNetworkLoadBalancer.View::getTargetPool)
              .distinct()
              .collect(Collectors.toList());
    }
    return info;
  }

  protected Image buildBootImage(BasicGoogleDeployDescription description, Task task) {
    return GCEUtil.getBootImage(
        description,
        task,
        BASE_PHASE,
        clouddriverUserAgentApplicationName,
        googleConfigurationProperties.getBaseImageProjects(),
        safeRetry,
        this);
  }

  protected List<AttachedDisk> buildAttachedDisks(
      BasicGoogleDeployDescription description, Task task, Image bootImage) {
    return GCEUtil.buildAttachedDisks(
        description,
        null,
        false,
        googleDeployDefaults,
        task,
        BASE_PHASE,
        clouddriverUserAgentApplicationName,
        googleConfigurationProperties.getBaseImageProjects(),
        bootImage,
        safeRetry,
        this);
  }

  protected NetworkInterface buildNetworkInterface(
      BasicGoogleDeployDescription description, GoogleNetwork network, GoogleSubnet subnet) {
    boolean associatePublicIpAddress =
        description.getAssociatePublicIpAddress() == null
            || description.getAssociatePublicIpAddress();
    return GCEUtil.buildNetworkInterface(
        network, subnet, associatePublicIpAddress, ACCESS_CONFIG_NAME, ACCESS_CONFIG_TYPE);
  }

  protected boolean hasBackedServiceFromInput(
      BasicGoogleDeployDescription description, LoadBalancerInfo loadBalancerInfo) {
    Map<String, String> instanceMetadata = description.getInstanceMetadata();
    return (!instanceMetadata.isEmpty() && instanceMetadata.containsKey(BACKEND_SERVICE_NAMES))
        || !loadBalancerInfo.getSslLoadBalancers().isEmpty()
        || !loadBalancerInfo.getTcpLoadBalancers().isEmpty();
  }

  protected GoogleHttpLoadBalancingPolicy buildLoadBalancerPolicyFromInput(
      BasicGoogleDeployDescription description) throws JsonProcessingException {
    Map<String, String> instanceMetadata = description.getInstanceMetadata();
    String sourcePolicyJson = instanceMetadata.get(LOAD_BALANCING_POLICY);
    if (description.getLoadBalancingPolicy() != null
        && description.getLoadBalancingPolicy().getBalancingMode() != null) {
      return description.getLoadBalancingPolicy();
    } else if (StringUtils.isNotBlank(sourcePolicyJson)) {
      return objectMapper.readValue(sourcePolicyJson, GoogleHttpLoadBalancingPolicy.class);
    } else {
      log.warn(
          "No load balancing policy found in the operation description or the source server group, adding defaults");
      GoogleHttpLoadBalancingPolicy policy = new GoogleHttpLoadBalancingPolicy();
      policy.setBalancingMode(GoogleLoadBalancingPolicy.BalancingMode.UTILIZATION);
      policy.setMaxUtilization(0.80f);
      policy.setCapacityScaler(1.0f);
      NamedPort namedPort = new NamedPort();
      namedPort.setName(GoogleHttpLoadBalancingPolicy.HTTP_DEFAULT_PORT_NAME);
      namedPort.setPort(GoogleHttpLoadBalancingPolicy.getHTTP_DEFAULT_PORT());
      policy.setNamedPorts(List.of(namedPort));
      return policy;
    }
  }

  protected List<BackendService> getBackendServiceToUpdate(
      BasicGoogleDeployDescription description,
      String serverGroupName,
      LoadBalancerInfo lbInfo,
      GoogleHttpLoadBalancingPolicy policy,
      String region) {
    // Resolve and queue the backend service updates, but don't execute yet.
    // We need to resolve this information to set metadata in the template so enable can know about
    // the
    // load balancing policy this server group was configured with.
    // If we try to execute the update, GCP will fail since the MIG is not created yet.
    if (hasBackedServiceFromInput(description, lbInfo)) {
      List<BackendService> backendServicesToUpdate = new ArrayList<>();
      Map<String, String> instanceMetadata = description.getInstanceMetadata();
      List<String> backendServices =
          instanceMetadata.get(BACKEND_SERVICE_NAMES) != null
              ? new ArrayList<>(
                  Arrays.asList(instanceMetadata.get(BACKEND_SERVICE_NAMES).split(",")))
              : new ArrayList<>();
      backendServices.addAll(
          lbInfo.getSslLoadBalancers().stream()
              .map(lb -> (GoogleSslLoadBalancer.View) lb)
              .map(it -> it.getBackendService().getName())
              .collect(Collectors.toList()));
      backendServices.addAll(
          lbInfo.getTcpLoadBalancers().stream()
              .map(lb -> (GoogleTcpLoadBalancer.View) lb)
              .map(it -> it.getBackendService().getName())
              .collect(Collectors.toList()));

      // Set the load balancer name metadata.
      List<String> globalLbNames =
          lbInfo.getSslLoadBalancers().stream()
              .map(GoogleLoadBalancerView::getName)
              .collect(Collectors.toList());
      globalLbNames.addAll(
          lbInfo.getTcpLoadBalancers().stream()
              .map(GoogleLoadBalancerView::getName)
              .collect(Collectors.toList()));
      globalLbNames.addAll(
          GCEUtil.resolveHttpLoadBalancerNamesMetadata(
              backendServices,
              description.getCredentials().getCompute(),
              description.getCredentials().getProject(),
              this));
      instanceMetadata.put(GLOBAL_LOAD_BALANCER_NAMES, String.join(",", globalLbNames));

      backendServices.forEach(
          backendServiceName -> {
            try {
              BackendService backendService =
                  getBackendServiceFromProvider(description.getCredentials(), backendServiceName);
              GCEUtil.updateMetadataWithLoadBalancingPolicy(policy, instanceMetadata, objectMapper);
              Backend backendToAdd = GCEUtil.backendFromLoadBalancingPolicy(policy);
              if (description.getRegional()) {
                backendToAdd.setGroup(
                    GCEUtil.buildRegionalServerGroupUrl(
                        description.getCredentials().getProject(), region, serverGroupName));
              } else {
                backendToAdd.setGroup(
                    GCEUtil.buildZonalServerGroupUrl(
                        description.getCredentials().getProject(),
                        description.getZone(),
                        serverGroupName));
              }
              if (backendService.getBackends() == null) {
                backendService.setBackends(new ArrayList<>());
              }
              backendService.getBackends().add(backendToAdd);
              backendServicesToUpdate.add(backendService);
            } catch (IOException e) {
              log.error(e.getMessage());
            }
          });
      return backendServicesToUpdate;
    }
    return Collections.emptyList();
  }

  protected List<BackendService> getRegionBackendServicesToUpdate(
      BasicGoogleDeployDescription description,
      String serverGroupName,
      LoadBalancerInfo lbInfo,
      GoogleHttpLoadBalancingPolicy policy,
      String region) {
    if (!lbInfo.getInternalLoadBalancers().isEmpty()
        || !lbInfo.getInternalHttpLoadBalancers().isEmpty()) {
      List<BackendService> regionBackendServicesToUpdate = new ArrayList<>();
      Map<String, String> instanceMetadata = description.getInstanceMetadata();
      List<String> existingRegionalLbs =
          instanceMetadata.get(REGIONAL_LOAD_BALANCER_NAMES) != null
              ? new ArrayList<>(
                  Arrays.asList(instanceMetadata.get(REGIONAL_LOAD_BALANCER_NAMES).split(",")))
              : new ArrayList<>();
      List<String> regionBackendServices =
          instanceMetadata.get(REGION_BACKEND_SERVICE_NAMES) != null
              ? new ArrayList<>(
                  Arrays.asList(instanceMetadata.get(REGION_BACKEND_SERVICE_NAMES).split(",")))
              : new ArrayList<>();
      List<String> ilbServices =
          lbInfo.getInternalLoadBalancers().stream()
              .map(lb -> (GoogleInternalLoadBalancer.View) lb)
              .map(it -> it.getBackendService().getName())
              .collect(Collectors.toList());
      ilbServices.addAll(regionBackendServices);
      ilbServices.stream().distinct().collect(Collectors.toList());
      List<String> ilbNames =
          lbInfo.getInternalLoadBalancers().stream()
              .map(GoogleLoadBalancerView::getName)
              .collect(Collectors.toList());
      ilbNames.addAll(
          lbInfo.getInternalHttpLoadBalancers().stream()
              .map(GoogleLoadBalancerView::getName)
              .collect(Collectors.toList()));

      ilbNames.forEach(
          ilbName -> {
            if (!existingRegionalLbs.contains(ilbName)) {
              existingRegionalLbs.add(ilbName);
            }
          });
      instanceMetadata.put(REGIONAL_LOAD_BALANCER_NAMES, String.join(",", existingRegionalLbs));

      List<String> internalHttpLbBackendServices =
          lbInfo.getInternalHttpLoadBalancers().stream()
              .map(lb -> (GoogleInternalHttpLoadBalancer.InternalHttpLbView) lb)
              .map(Utils::getBackendServicesFromInternalHttpLoadBalancerView)
              .flatMap(Collection::stream)
              .map(GoogleBackendService::getName)
              .collect(Collectors.toList());

      ilbServices.forEach(
          backendServiceName -> {
            try {
              BackendService backendService =
                  getRegionBackendServiceFromProvider(
                      description.getCredentials(), region, serverGroupName);
              Backend backendToAdd;
              if (internalHttpLbBackendServices.contains(backendServiceName)) {
                backendToAdd = GCEUtil.backendFromLoadBalancingPolicy(policy);
              } else {
                backendToAdd = new Backend();
              }
              if (description.getRegional()) {
                backendToAdd.setGroup(
                    GCEUtil.buildRegionalServerGroupUrl(
                        description.getCredentials().getProject(), region, serverGroupName));
              } else {
                backendToAdd.setGroup(
                    GCEUtil.buildZonalServerGroupUrl(
                        description.getCredentials().getProject(),
                        description.getZone(),
                        serverGroupName));
              }

              if (backendService.getBackends() == null) {
                backendService.setBackends(new ArrayList<>());
              }
              backendService.getBackends().add(backendToAdd);
              regionBackendServicesToUpdate.add(backendService);
            } catch (IOException e) {
              log.error(e.getMessage());
            }
          });
      return regionBackendServicesToUpdate;
    }
    return Collections.emptyList();
  }

  protected void addUserDataToInstanceMetadata(
      BasicGoogleDeployDescription description,
      String serverGroupName,
      String instanceTemplateName,
      Task task) {
    Map userDataMap = getUserData(description, serverGroupName, instanceTemplateName, task);
    Map<String, String> instanceMetadata = description.getInstanceMetadata();
    if (!instanceMetadata.isEmpty()) {
      instanceMetadata.putAll(userDataMap);
    } else {
      instanceMetadata = userDataMap;
    }
    description.setInstanceMetadata(instanceMetadata);
  }

  protected Map getUserData(
      BasicGoogleDeployDescription description,
      String serverGroupName,
      String instanceTemplateName,
      Task task) {
    String customUserData =
        StringUtils.isNotBlank(description.getUserData()) ? description.getUserData() : "";
    Map userData =
        googleUserDataProvider.getUserData(
            serverGroupName,
            instanceTemplateName,
            description,
            description.getCredentials(),
            customUserData);
    task.updateStatus(BASE_PHASE, "Resolved user data.");
    return userData;
  }

  protected void addSelectZonesToInstanceMetadata(BasicGoogleDeployDescription description) {
    if (description.getRegional() && description.getSelectZones()) {
      Map<String, String> instanceMetadata = description.getInstanceMetadata();
      instanceMetadata.put(SELECT_ZONES, "true");
      description.setInstanceMetadata(instanceMetadata);
    }
  }

  protected Metadata buildMetadataFromInstanceMetadata(BasicGoogleDeployDescription description) {
    return GCEUtil.buildMetadataFromMap(description.getInstanceMetadata());
  }

  protected Tags buildTagsFromInput(BasicGoogleDeployDescription description) {
    return GCEUtil.buildTagsFromList(description.getTags());
  }

  protected List<ServiceAccount> buildServiceAccountFromInput(
      BasicGoogleDeployDescription description) {
    if (!description.getAuthScopes().isEmpty()
        && StringUtils.isBlank(description.getServiceAccountEmail())) {
      description.setServiceAccountEmail("default");
    }

    return GCEUtil.buildServiceAccount(
        description.getServiceAccountEmail(), description.getAuthScopes());
  }

  protected Scheduling buildSchedulingFromInput(BasicGoogleDeployDescription description) {
    return GCEUtil.buildScheduling(description);
  }

  protected Map<String, String> buildLabelsFromInput(
      BasicGoogleDeployDescription description, String serverGroupName, String region) {
    Map<String, String> labels = description.getLabels();
    if (labels == null) {
      labels = new HashMap<>();
    }

    // Used to group instances when querying for metrics from kayenta.
    labels.put("spinnaker-region", region);
    labels.put("spinnaker-server-group", serverGroupName);
    return labels;
  }

  private void setupMonikerForOperation(
      BasicGoogleDeployDescription description, String serverGroupName, String clusterName) {
    Namer<GoogleLabeledResource> namer =
        NamerRegistry.lookup()
            .withProvider(GoogleCloudProvider.getID())
            .withAccount(description.getAccountName())
            .withResource(GoogleLabeledResource.class);

    Integer sequence = Names.parseName(serverGroupName).getSequence();

    Moniker moniker =
        Moniker.builder()
            .app(description.getApplication())
            .cluster(clusterName)
            .detail(description.getFreeFormDetails())
            .stack(description.getStack())
            .sequence(sequence)
            .build();

    // Apply moniker to labels which are subsequently recorded in the instance template.
    GoogleInstanceTemplate googleInstanceTemplate = new GoogleInstanceTemplate();
    googleInstanceTemplate.labels = description.getLabels();
    namer.applyMoniker(googleInstanceTemplate, moniker);
  }

  protected void validateAcceleratorConfig(BasicGoogleDeployDescription description) {
    // Accelerators are supported for zonal server groups only.
    if (description.getAcceleratorConfigs() != null
        && !description.getAcceleratorConfigs().isEmpty()
        && (!description.getRegional() || description.getSelectZones())) {
      throw new IllegalArgumentException(
          "Accelerators are only supported with regional server groups if the zones are specified by the user.");
    }
  }

  protected InstanceProperties buildInstancePropertiesFromInput(
      BasicGoogleDeployDescription description,
      String machineTypeName,
      List<AttachedDisk> attachedDisks,
      NetworkInterface networkInterface,
      Metadata metadata,
      Tags tags,
      List<ServiceAccount> serviceAccounts,
      Scheduling scheduling,
      Map<String, String> labels) {
    return new InstanceProperties()
        .setMachineType(machineTypeName)
        .setDisks(attachedDisks)
        .setGuestAccelerators(
            description.getAcceleratorConfigs() != null
                    && !description.getAcceleratorConfigs().isEmpty()
                ? description.getAcceleratorConfigs()
                : Collections.emptyList())
        .setNetworkInterfaces(List.of(networkInterface))
        .setCanIpForward(description.getCanIpForward())
        .setMetadata(metadata)
        .setTags(tags)
        .setLabels(labels)
        .setScheduling(scheduling)
        .setServiceAccounts(serviceAccounts)
        .setResourceManagerTags(description.getResourceManagerTags())
        .setPartnerMetadata(description.getPartnerMetadata());
  }

  protected void addShieldedVmConfigToInstanceProperties(
      BasicGoogleDeployDescription description,
      InstanceProperties instanceProperties,
      Image bootImage) {
    if (GCEUtil.isShieldedVmCompatible(bootImage)) {
      instanceProperties.setShieldedVmConfig(GCEUtil.buildShieldedVmConfig(description));
    }
  }

  protected void addMinCpuPlatformToInstanceProperties(
      BasicGoogleDeployDescription description, InstanceProperties instanceProperties) {
    if (StringUtils.isNotBlank(description.getMinCpuPlatform())) {
      instanceProperties.setMinCpuPlatform(description.getMinCpuPlatform());
    }
  }

  protected InstanceTemplate buildInstanceTemplate(String name, InstanceProperties properties) {
    return new InstanceTemplate().setName(name).setProperties(properties);
  }

  protected void setCapacityFromInput(BasicGoogleDeployDescription description) {
    if (description.getCapacity() != null) {
      description.setTargetSize(description.getCapacity().getDesired());
    }
  }

  protected void setAutoscalerCapacityFromInput(BasicGoogleDeployDescription description) {
    if (autoscalerIsSpecified(description)) {
      if (description.getCapacity() != null) {
        description.getAutoscalingPolicy().setMinNumReplicas(description.getCapacity().getMin());
        description.getAutoscalingPolicy().setMaxNumReplicas(description.getCapacity().getMax());
      }
      GCEUtil.calibrateTargetSizeWithAutoscaler(description);
    }
  }

  protected boolean autoscalerIsSpecified(BasicGoogleDeployDescription description) {
    return description.getAutoscalingPolicy() != null
        && (description.getAutoscalingPolicy().getCpuUtilization() != null
            || description.getAutoscalingPolicy().getLoadBalancingUtilization() != null
            || description.getAutoscalingPolicy().getCustomMetricUtilizations() != null
            || description.getAutoscalingPolicy().getScalingSchedules() != null);
  }

  protected void setCapacityFromSource(BasicGoogleDeployDescription description, Task task) {
    BasicGoogleDeployDescription.Source source = description.getSource();
    if (source != null
        && source.getUseSourceCapacity() != null
        && source.getUseSourceCapacity()
        && StringUtils.isNotBlank(source.getRegion())
        && StringUtils.isNotBlank(source.getServerGroupName())) {
      task.updateStatus(
          BASE_PHASE,
          String.format(
              "Looking up server group %s in %s in order to copy the current capacity...",
              source.getServerGroupName(), source.getRegion()));

      // Locate the ancestor server group.
      GoogleServerGroup.View ancestorServerGroup =
          GCEUtil.queryServerGroup(
              googleClusterProvider,
              description.getAccountName(),
              source.getRegion(),
              source.getServerGroupName());
      description.setTargetSize(ancestorServerGroup.getCapacity().getDesired());
      description.setAutoscalingPolicy(ancestorServerGroup.getAutoscalingPolicy());
    }
  }

  protected List<InstanceGroupManagerAutoHealingPolicy> buildAutoHealingPolicyFromInput(
      BasicGoogleDeployDescription description, Task task) {
    GoogleHealthCheck autoHealingHealthCheck = null;
    if (description.getAutoHealingPolicy() != null
        && StringUtils.isNotBlank(description.getAutoHealingPolicy().getHealthCheck())) {
      autoHealingHealthCheck =
          (GoogleHealthCheck)
              GCEUtil.queryHealthCheck(
                  description.getCredentials().getProject(),
                  description.getAccountName(),
                  description.getAutoHealingPolicy().getHealthCheck(),
                  description.getAutoHealingPolicy().getHealthCheckKind(),
                  description.getCredentials().getCompute(),
                  cacheView,
                  task,
                  BASE_PHASE,
                  this);
    }
    List<InstanceGroupManagerAutoHealingPolicy> autoHealingPolicy = null;
    if (autoHealingHealthCheck != null) {
      InstanceGroupManagerAutoHealingPolicy policy = new InstanceGroupManagerAutoHealingPolicy();
      policy.setHealthCheck(autoHealingHealthCheck.getSelfLink());
      policy.setInitialDelaySec(description.getAutoHealingPolicy().getInitialDelaySec());
      autoHealingPolicy = List.of(policy);
    }

    if (autoHealingPolicy != null
        && description.getAutoHealingPolicy().getMaxUnavailable() != null) {
      FixedOrPercent maxUnavailable = new FixedOrPercent();
      maxUnavailable.setFixed(
          description.getAutoHealingPolicy().getMaxUnavailable().getFixed().intValue());
      maxUnavailable.setPercent(
          description.getAutoHealingPolicy().getMaxUnavailable().getPercent().intValue());
      autoHealingPolicy.get(0).set("maxUnavailable", maxUnavailable);
    }
    return autoHealingPolicy;
  }

  protected InstanceGroupManager buildInstanceGroupFromInput(
      BasicGoogleDeployDescription description,
      String serverGroupName,
      String instanceTemplateUrl,
      List<String> targetPools,
      List<InstanceGroupManagerAutoHealingPolicy> autoHealingPolicy) {
    return new InstanceGroupManager()
        .setName(serverGroupName)
        .setBaseInstanceName(serverGroupName)
        .setInstanceTemplate(instanceTemplateUrl)
        .setTargetSize(description.getTargetSize())
        .setTargetPools(targetPools)
        .setAutoHealingPolicies(autoHealingPolicy);
  }

  protected void setNamedPortsToInstanceGroup(
      BasicGoogleDeployDescription description,
      LoadBalancerInfo lbInfo,
      InstanceGroupManager instanceGroupManager) {
    if ((hasBackedServiceFromInput(description, lbInfo)
            || !lbInfo.internalHttpLoadBalancers.isEmpty())
        && (description.getLoadBalancingPolicy() != null
            || (description.getSource() != null
                && StringUtils.isNotBlank(description.getSource().getServerGroupName())))) {
      List<NamedPort> namedPorts = new ArrayList<>();
      String sourceGroupName = description.getSource().getServerGroupName();

      // Note: this favors the explicitly specified load balancing policy over the source server
      // group.
      if (StringUtils.isNotBlank(sourceGroupName) && description.getLoadBalancingPolicy() == null) {
        GoogleServerGroup.View sourceServerGroup =
            googleClusterProvider.getServerGroup(
                description.getAccountName(), description.getSource().getRegion(), sourceGroupName);
        if (sourceServerGroup == null) {
          log.warn(
              String.format(
                  "Could not locate source server group %s to update named port.",
                  sourceGroupName));
        } else {
          namedPorts =
              sourceServerGroup.getNamedPorts().entrySet().stream()
                  .map(entry -> new NamedPort().setName(entry.getKey()).setPort(entry.getValue()))
                  .collect(Collectors.toList());
        }
      } else {
        if (description.getLoadBalancingPolicy().getNamedPorts() != null) {
          namedPorts = description.getLoadBalancingPolicy().getNamedPorts();
        } else if (description.getLoadBalancingPolicy().getListeningPort() != null) {
          log.warn(
              "Deriving named ports from deprecated 'listeningPort' attribute. Please update your deploy description to use 'namedPorts'.");
          namedPorts.add(
              new NamedPort()
                  .setName(GoogleHttpLoadBalancingPolicy.HTTP_DEFAULT_PORT_NAME)
                  .setPort(description.getLoadBalancingPolicy().getListeningPort()));
        }
      }

      if (namedPorts.isEmpty()) {
        log.warn(
            "Could not locate named port on either load balancing policy or source server group. Setting default named port.");
        namedPorts.add(
            new NamedPort()
                .setName(GoogleHttpLoadBalancingPolicy.HTTP_DEFAULT_PORT_NAME)
                .setPort(GoogleHttpLoadBalancingPolicy.getHTTP_DEFAULT_PORT()));
      }
      instanceGroupManager.setNamedPorts(namedPorts);
    }
  }

  protected void createInstanceGroupManagerFromInput(
      BasicGoogleDeployDescription description,
      InstanceGroupManager instanceGroupManager,
      LoadBalancerInfo lbInfo,
      String serverGroupName,
      String region,
      Task task)
      throws IOException {
    if (description.getRegional()) {
      setDistributionPolicyToInstanceGroup(description, instanceGroupManager);
      String targetLink =
          createRegionalInstanceGroupManagerAndWait(
              description, lbInfo, serverGroupName, region, instanceGroupManager, task);
      createRegionalAutoscaler(description, serverGroupName, targetLink, region, task);
    } else {
      String targetLink =
          createInstanceGroupManagerAndWait(
              description, lbInfo, serverGroupName, instanceGroupManager, task);
      createAutoscaler(description, serverGroupName, targetLink, task);
    }
  }

  protected void setDistributionPolicyToInstanceGroup(
      BasicGoogleDeployDescription description, InstanceGroupManager instanceGroupManager) {
    if (description.getDistributionPolicy() != null) {
      DistributionPolicy distributionPolicy = new DistributionPolicy();

      if (description.getSelectZones()
          && !description.getDistributionPolicy().getZones().isEmpty()) {
        log.info(
            String.format(
                "Configuring explicit zones selected for regional server group: %s",
                String.join(", ", description.getDistributionPolicy().getZones())));
        List<DistributionPolicyZoneConfiguration> selectedZones =
            description.getDistributionPolicy().getZones().stream()
                .map(
                    it ->
                        new DistributionPolicyZoneConfiguration()
                            .setZone(
                                GCEUtil.buildZoneUrl(
                                    description.getCredentials().getProject(), it)))
                .collect(Collectors.toList());
        distributionPolicy.setZones(selectedZones);
      }

      if (StringUtils.isNotBlank(description.getDistributionPolicy().getTargetShape())) {
        distributionPolicy.setTargetShape(description.getDistributionPolicy().getTargetShape());
      }

      if (!distributionPolicy.getZones().isEmpty()
          || StringUtils.isNotBlank(distributionPolicy.getTargetShape())) {
        instanceGroupManager.setDistributionPolicy(distributionPolicy);
      }
    }
  }

  private void updateBackendServices(
      BasicGoogleDeployDescription description,
      LoadBalancerInfo lbInfo,
      String serverGroupName,
      List<BackendService> backendServicesToUpdate,
      Task task) {
    if (!description.getDisableTraffic() && hasBackedServiceFromInput(description, lbInfo)) {
      backendServicesToUpdate.forEach(
          backendService -> {
            safeRetry.doRetry(
                updateBackendServices(
                    description.getCredentials().getCompute(),
                    description.getCredentials().getProject(),
                    backendService.getName(),
                    backendService),
                "Load balancer backend service",
                task,
                List.of(400, 412),
                Collections.emptyList(),
                Map.of(
                    "action",
                    "update",
                    "phase",
                    BASE_PHASE,
                    "operation",
                    "updateBackendServices",
                    TAG_SCOPE,
                    SCOPE_GLOBAL),
                registry);
            task.updateStatus(
                BASE_PHASE,
                String.format(
                    "Done associating server group %s with backend service %s.",
                    serverGroupName, backendService.getName()));
          });
    }
  }

  private void updateRegionalBackendServices(
      BasicGoogleDeployDescription description,
      LoadBalancerInfo lbInfo,
      String serverGroupName,
      String region,
      List<BackendService> regionBackendServicesToUpdate,
      Task task) {
    if (!description.getDisableTraffic()
        && (!lbInfo.internalLoadBalancers.isEmpty()
            || !lbInfo.internalHttpLoadBalancers.isEmpty())) {
      regionBackendServicesToUpdate.forEach(
          backendService -> {
            safeRetry.doRetry(
                updateBackendServices(
                    description.getCredentials().getCompute(),
                    description.getCredentials().getProject(),
                    backendService.getName(),
                    backendService,
                    region),
                "Internal load balancer backend service",
                task,
                List.of(400, 412),
                Collections.emptyList(),
                Map.of(
                    "action",
                    "update",
                    "phase",
                    BASE_PHASE,
                    "operation",
                    "updateRegionBackendServices",
                    TAG_SCOPE,
                    SCOPE_REGIONAL,
                    TAG_REGION,
                    region),
                registry);
            task.updateStatus(
                BASE_PHASE,
                String.format(
                    "Done associating server group %s with backend service %s.",
                    serverGroupName, backendService.getName()));
          });
    }
  }

  protected BackendService getBackendServiceFromProvider(
      GoogleNamedAccountCredentials credentials, String backendServiceName) throws IOException {
    return timeExecute(
        credentials
            .getCompute()
            .backendServices()
            .get(credentials.getProject(), backendServiceName),
        "compute.backendServices.get",
        TAG_SCOPE,
        SCOPE_GLOBAL);
  }

  protected BackendService getRegionBackendServiceFromProvider(
      GoogleNamedAccountCredentials credentials, String region, String backendServiceName)
      throws IOException {
    return timeExecute(
        credentials
            .getCompute()
            .regionBackendServices()
            .get(credentials.getProject(), region, backendServiceName),
        "compute.regionBackendServices.get",
        TAG_SCOPE,
        SCOPE_REGIONAL,
        TAG_REGION,
        region);
  }

  private String createInstanceTemplateAndWait(
      GoogleNamedAccountCredentials credentials, InstanceTemplate template, Task task)
      throws IOException {
    Operation instanceTemplateCreateOperation =
        timeExecute(
            credentials.getCompute().instanceTemplates().insert(credentials.getProject(), template),
            "compute.instanceTemplates.insert",
            TAG_SCOPE,
            SCOPE_GLOBAL);
    String instanceTemplateUrl = instanceTemplateCreateOperation.getTargetLink();

    // Before building the managed instance group we must check and wait until the instance template
    // is built.
    googleOperationPoller.waitForGlobalOperation(
        credentials.getCompute(),
        credentials.getProject(),
        instanceTemplateCreateOperation.getName(),
        null,
        task,
        "instance template " + GCEUtil.getLocalName(instanceTemplateUrl),
        BASE_PHASE);
    return instanceTemplateUrl;
  }

  protected String createRegionalInstanceGroupManagerAndWait(
      BasicGoogleDeployDescription description,
      LoadBalancerInfo lbInfo,
      String serverGroupName,
      String region,
      InstanceGroupManager instanceGroupManager,
      Task task)
      throws IOException {
    Operation migCreateOperation =
        timeExecute(
            description
                .getCredentials()
                .getCompute()
                .regionInstanceGroupManagers()
                .insert(description.getCredentials().getProject(), region, instanceGroupManager),
            "compute.regionInstanceGroupManagers.insert",
            TAG_SCOPE,
            SCOPE_REGIONAL,
            TAG_REGION,
            region);

    if ((!description.getDisableTraffic() && hasBackedServiceFromInput(description, lbInfo))
        || autoscalerIsSpecified(description)
        || (!description.getDisableTraffic()
            && (!lbInfo.internalLoadBalancers.isEmpty()
                || !lbInfo.internalHttpLoadBalancers.isEmpty()))) {
      // Before updating the Backend Services or creating the Autoscaler we must wait until the
      // managed instance group is created.
      googleOperationPoller.waitForRegionalOperation(
          description.getCredentials().getCompute(),
          description.getCredentials().getProject(),
          region,
          migCreateOperation.getName(),
          null,
          task,
          String.format("managed instance group %s", serverGroupName),
          BASE_PHASE);
    }
    return migCreateOperation.getTargetLink();
  }

  protected void createRegionalAutoscaler(
      BasicGoogleDeployDescription description,
      String serverGroupName,
      String targetLink,
      String region,
      Task task)
      throws IOException {
    if (autoscalerIsSpecified(description)) {
      task.updateStatus(
          BASE_PHASE, String.format("Creating regional autoscaler for %s...", serverGroupName));

      Autoscaler autoscaler =
          GCEUtil.buildAutoscaler(serverGroupName, targetLink, description.getAutoscalingPolicy());

      timeExecute(
          description
              .getCredentials()
              .getCompute()
              .regionAutoscalers()
              .insert(description.getCredentials().getProject(), region, autoscaler),
          "compute.regionAutoscalers.insert",
          TAG_SCOPE,
          SCOPE_REGIONAL,
          TAG_REGION,
          region);
    }
  }

  protected String createInstanceGroupManagerAndWait(
      BasicGoogleDeployDescription description,
      LoadBalancerInfo lbInfo,
      String serverGroupName,
      InstanceGroupManager instanceGroupManager,
      Task task)
      throws IOException {
    Operation createOperation =
        timeExecute(
            description
                .getCredentials()
                .getCompute()
                .instanceGroupManagers()
                .insert(
                    description.getCredentials().getProject(),
                    description.getZone(),
                    instanceGroupManager),
            "compute.instanceGroupManagers.insert",
            TAG_SCOPE,
            SCOPE_ZONAL,
            TAG_ZONE,
            description.getZone());

    if ((!description.getDisableTraffic() && hasBackedServiceFromInput(description, lbInfo))
        || autoscalerIsSpecified(description)
        || (!description.getDisableTraffic()
            && (!lbInfo.internalLoadBalancers.isEmpty()
                || !lbInfo.internalHttpLoadBalancers.isEmpty()))) {
      // Before updating the Backend Services or creating the Autoscaler we must wait until the
      // managed instance group is created.
      googleOperationPoller.waitForZonalOperation(
          description.getCredentials().getCompute(),
          description.getCredentials().getProject(),
          description.getZone(),
          createOperation.getName(),
          null,
          task,
          String.format("managed instance group %s", serverGroupName),
          BASE_PHASE);
    }
    return createOperation.getTargetLink();
  }

  protected void createAutoscaler(
      BasicGoogleDeployDescription description,
      String serverGroupName,
      String targetLink,
      Task task)
      throws IOException {
    if (autoscalerIsSpecified(description)) {
      task.updateStatus(
          BASE_PHASE, String.format("Creating zonal autoscaler for %s...", serverGroupName));

      Autoscaler autoscaler =
          GCEUtil.buildAutoscaler(serverGroupName, targetLink, description.getAutoscalingPolicy());

      timeExecute(
          description
              .getCredentials()
              .getCompute()
              .autoscalers()
              .insert(description.getCredentials().getProject(), description.getZone(), autoscaler),
          "compute.autoscalers.insert",
          TAG_SCOPE,
          SCOPE_ZONAL,
          TAG_ZONE,
          description.getZone());
    }
  }

  private Closure updateBackendServices(
      Compute compute, String project, String backendServiceName, BackendService backendService) {
    return new Closure<>(this, this) {
      @Override
      public Object call() {
        BackendService serviceToUpdate = null;
        try {
          serviceToUpdate =
              timeExecute(
                  compute.backendServices().get(project, backendServiceName),
                  "compute.backendServices.get",
                  TAG_SCOPE,
                  SCOPE_GLOBAL);
        } catch (IOException e) {
          log.error(e.getMessage());
        }
        if (serviceToUpdate.getBackends() == null) {
          serviceToUpdate.setBackends(new ArrayList<>());
        }
        BackendService finalServiceToUpdate = serviceToUpdate;
        backendService.getBackends().forEach(it -> finalServiceToUpdate.getBackends().add(it));
        Set<String> seenGroup = new HashSet<>();
        serviceToUpdate.getBackends().stream()
            .filter(backend -> seenGroup.add(backend.getGroup()))
            .collect(Collectors.toList());
        try {
          timeExecute(
              compute.backendServices().update(project, backendServiceName, serviceToUpdate),
              "compute.backendServices.update",
              TAG_SCOPE,
              SCOPE_GLOBAL);
        } catch (IOException e) {
          log.error(e.getMessage());
        }
        return null;
      }
    };
  }

  private Closure updateBackendServices(
      Compute compute,
      String project,
      String backendServiceName,
      BackendService backendService,
      String region) {
    return new Closure<>(this, this) {
      @Override
      public Object call() {
        BackendService serviceToUpdate = null;
        try {
          serviceToUpdate =
              timeExecute(
                  compute.backendServices().get(project, backendServiceName),
                  "compute.regionBackendServices.get",
                  TAG_SCOPE,
                  SCOPE_GLOBAL);
        } catch (IOException e) {
          log.error(e.getMessage());
        }
        if (serviceToUpdate.getBackends() == null) {
          serviceToUpdate.setBackends(new ArrayList<>());
        }
        BackendService finalServiceToUpdate = serviceToUpdate;
        backendService.getBackends().forEach(it -> finalServiceToUpdate.getBackends().add(it));
        Set<String> seenGroup = new HashSet<>();
        serviceToUpdate.getBackends().stream()
            .filter(backend -> seenGroup.add(backend.getGroup()))
            .collect(Collectors.toList());
        try {
          timeExecute(
              compute.backendServices().update(project, backendServiceName, serviceToUpdate),
              "compute.regionBackendServices.update",
              TAG_SCOPE,
              SCOPE_GLOBAL);
        } catch (IOException e) {
          log.error(e.getMessage());
        }
        return null;
      }
    };
  }

  @Override
  public boolean handles(DeployDescription description) {
    return description instanceof BasicGoogleDeployDescription;
  }

  @Override
  public Registry getRegistry() {
    return registry;
  }

  @Data
  class LoadBalancerInfo {
    List<String> targetPools = new ArrayList<>();
    List<GoogleLoadBalancerView> internalLoadBalancers = new ArrayList<>();
    List<GoogleLoadBalancerView> internalHttpLoadBalancers = new ArrayList<>();
    List<GoogleLoadBalancerView> sslLoadBalancers = new ArrayList<>();
    List<GoogleLoadBalancerView> tcpLoadBalancers = new ArrayList<>();
  }

  static class GoogleInstanceTemplate implements GoogleLabeledResource {
    Map<String, String> labels;

    @Override
    public Map<String, String> getLabels() {
      return labels;
    }
  }
}
