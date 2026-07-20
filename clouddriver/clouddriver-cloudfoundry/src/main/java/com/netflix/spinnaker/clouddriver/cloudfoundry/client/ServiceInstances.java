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

package com.netflix.spinnaker.clouddriver.cloudfoundry.client;

import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClientUtils.*;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.LastOperation.State.*;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.LastOperation.Type.*;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.VcapServiceInstance.Type.MANAGED_SERVICE_INSTANCE;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.VcapServiceInstance.Type.USER_PROVIDED_SERVICE_INSTANCE;
import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.google.common.annotations.VisibleForTesting;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.ConfigService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.ServiceInstanceService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.ServiceInstanceResponse;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.CreateServiceCredentialBinding;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.CreateServiceInstance;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.CreateSharedServiceInstances;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.CreateUserProvidedServiceInstance;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.FeatureFlag;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.LastOperation;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.ServiceCredentialBinding;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.ServiceInstance;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.ServiceOffering;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.ServicePlan;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.SharedTo;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryServiceInstance;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryServicePlan;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ServiceInstances {
  private final ServiceInstanceService api;
  private final ConfigService configApi;
  private final Spaces spaces;

  public List<ServiceCredentialBinding> findAllServiceBindingsByServiceName(
      String region, String serviceName) {
    CloudFoundryServiceInstance serviceInstance = getServiceInstance(region, serviceName);
    if (serviceInstance == null) {
      return emptyList();
    }
    return findAllServiceBindingsByService(serviceInstance.getId());
  }

  public void createServiceBinding(
      String serviceInstanceGuid,
      String appGuid,
      String name,
      @Nullable Map<String, Object> parameters) {
    try {
      safelyCall(
          () ->
              api.createServiceBinding(
                  CreateServiceCredentialBinding.forAppBinding(
                      serviceInstanceGuid, appGuid, name, parameters)));
    } catch (CloudFoundryApiException e) {
      if (e.getErrorCode() == null) throw e;

      switch (e.getErrorCode()) {
        case SERVICE_INSTANCE_ALREADY_BOUND:
          return;
        default:
          throw e;
      }
    }
  }

  public List<ServiceCredentialBinding> findAllServiceBindingsByApp(String appGuid) {
    return collectPages("service bindings", pg -> api.getServiceBindings(pg, null, appGuid, "app"));
  }

  public List<ServiceCredentialBinding> findAllServiceBindingsByService(String serviceGuid) {
    return collectPages(
        "service bindings", pg -> api.getServiceBindings(pg, serviceGuid, null, "app"));
  }

  public void deleteServiceBinding(String serviceBindingGuid) {
    safelyCall(() -> api.deleteServiceBinding(serviceBindingGuid));
  }

  @Nullable
  private ServiceOffering findServiceOfferingByName(String serviceName) {
    List<ServiceOffering> offerings =
        collectPages("service offerings by name", pg -> api.findServiceOfferings(pg, serviceName));
    return offerings.isEmpty() ? null : offerings.get(0);
  }

  private List<CloudFoundryServicePlan> findAllServicePlansByServiceName(String serviceName) {
    ServiceOffering offering = findServiceOfferingByName(serviceName);
    if (offering == null) {
      return emptyList();
    }
    List<ServicePlan> plans =
        collectPages(
            "service plans by offering", pg -> api.findServicePlans(pg, offering.getGuid(), null));

    return plans.stream()
        .map(
            plan ->
                CloudFoundryServicePlan.builder().name(plan.getName()).id(plan.getGuid()).build())
        .collect(toList());
  }

  public List<CloudFoundryService> findAllServicesByRegion(String region) {
    return spaces
        .findSpaceByRegion(region)
        .map(
            space -> {
              List<ServicePlan> visiblePlans =
                  collectPages(
                      "service plans visible to space",
                      pg -> api.findServicePlans(pg, null, space.getId()));

              Map<String, List<ServicePlan>> plansByOfferingGuid =
                  visiblePlans.stream()
                      .filter(plan -> plan.getServiceOfferingGuid() != null)
                      .collect(groupingBy(ServicePlan::getServiceOfferingGuid));

              return plansByOfferingGuid.entrySet().stream()
                  .map(
                      entry ->
                          safelyCall(() -> api.findServiceOfferingByGuid(entry.getKey()))
                              .map(
                                  offering ->
                                      CloudFoundryService.builder()
                                          .name(offering.getName())
                                          .servicePlans(
                                              entry.getValue().stream()
                                                  .map(
                                                      plan ->
                                                          CloudFoundryServicePlan.builder()
                                                              .name(plan.getName())
                                                              .id(plan.getGuid())
                                                              .build())
                                                  .collect(toList()))
                                          .build()))
                  .filter(Optional::isPresent)
                  .map(Optional::get)
                  .collect(toList());
            })
        .orElse(Collections.emptyList());
  }

  public List<ServiceInstance> findAllServicesBySpaceAndNames(
      CloudFoundrySpace space, List<String> serviceInstanceNames) {
    if (serviceInstanceNames == null || serviceInstanceNames.isEmpty()) return emptyList();
    String namesQuery = String.join(",", serviceInstanceNames);
    return collectPages("service instances", pg -> api.all(pg, namesQuery, space.getId(), null));
  }

  public List<ServiceInstance> findAllServiceInstancesBySpace(CloudFoundrySpace space) {
    return collectPages("service instances", pg -> api.all(pg, null, space.getId(), null));
  }

  // Visible for testing
  CloudFoundryServiceInstance getOsbServiceInstanceByRegion(
      String region, String serviceInstanceName) {
    CloudFoundrySpace space =
        spaces
            .findSpaceByRegion(region)
            .orElseThrow(() -> new CloudFoundryApiException("Cannot find region '" + region + "'"));
    ServiceInstance serviceInstance = getServiceInstanceByType(space, serviceInstanceName, null);
    return ofNullable(
            toCloudFoundryServiceInstance(
                serviceInstance, serviceInstance != null && serviceInstance.isManaged()))
        .orElseThrow(
            () ->
                new CloudFoundryApiException(
                    "Cannot find service '"
                        + serviceInstanceName
                        + "' in region '"
                        + space.getRegion()
                        + "'"));
  }

  private Set<CloudFoundrySpace> vetSharingOfServicesArgumentsAndGetSharingSpaces(
      String sharedFromRegion,
      @Nullable String serviceInstanceName,
      @Nullable Set<String> sharingRegions,
      String gerund) {
    if (isBlank(serviceInstanceName)) {
      throw new CloudFoundryApiException(
          "Please specify a name for the " + gerund + " service instance");
    }
    sharingRegions = ofNullable(sharingRegions).orElse(Collections.emptySet());
    if (sharingRegions.size() == 0) {
      throw new CloudFoundryApiException(
          "Please specify a list of regions for " + gerund + " '" + serviceInstanceName + "'");
    }

    return sharingRegions.stream()
        .map(
            r -> {
              if (sharedFromRegion.equals(r)) {
                throw new CloudFoundryApiException(
                    "Cannot specify 'org > space' as any of the " + gerund + " regions");
              }
              return spaces
                  .findSpaceByRegion(r)
                  .orElseThrow(
                      () ->
                          new CloudFoundryApiException(
                              "Cannot find region '" + r + "' for " + gerund));
            })
        .collect(toSet());
  }

  // Visible for testing
  Set<CloudFoundrySpace> vetUnshareServiceArgumentsAndGetSharingSpaces(
      @Nullable String serviceInstanceName, @Nullable Set<String> sharingRegions) {
    return vetSharingOfServicesArgumentsAndGetSharingSpaces(
        "", serviceInstanceName, sharingRegions, "unsharing");
  }

  // Visible for testing
  Set<CloudFoundrySpace> vetShareServiceArgumentsAndGetSharingSpaces(
      @Nullable String sharedFromRegion,
      @Nullable String serviceInstanceName,
      @Nullable Set<String> sharingRegions) {
    if (isBlank(sharedFromRegion)) {
      throw new CloudFoundryApiException(
          "Please specify a region for the sharing service instance");
    }
    return vetSharingOfServicesArgumentsAndGetSharingSpaces(
        sharedFromRegion, serviceInstanceName, sharingRegions, "sharing");
  }

  // Visible for testing
  Void checkServiceShareable(
      String serviceInstanceName, CloudFoundryServiceInstance serviceInstance) {
    FeatureFlag featureFlag =
        safelyCall(() -> configApi.getFeatureFlag("service_instance_sharing"))
            .orElseThrow(
                () ->
                    new CloudFoundryApiException(
                        "'service_instance_sharing' flag must be enabled in order to share services"));
    if (!featureFlag.isEnabled()) {
      throw new CloudFoundryApiException(
          "'service_instance_sharing' flag must be enabled in order to share services");
    }
    ServicePlan plan =
        safelyCall(() -> api.findServicePlanByServicePlanId(serviceInstance.getPlanId()))
            .orElseThrow(
                () ->
                    new CloudFoundryApiException(
                        "The service plan for 'new-service-plan-name' was not found"));
    ServiceOffering offering =
        safelyCall(() -> api.findServiceOfferingByGuid(plan.getServiceOfferingGuid()))
            .orElseThrow(
                () ->
                    new CloudFoundryApiException(
                        "The service broker for '" + serviceInstanceName + "' was not found"));

    if (!offering.isShareable()) {
      throw new CloudFoundryApiException(
          "The service broker must be configured as 'shareable' in order to share services");
    }

    return null;
  }

  public ServiceInstanceResponse shareServiceInstance(
      @Nullable String region,
      @Nullable String serviceInstanceName,
      @Nullable Set<String> shareToRegions) {
    Set<CloudFoundrySpace> shareToSpaces =
        vetShareServiceArgumentsAndGetSharingSpaces(region, serviceInstanceName, shareToRegions);
    CloudFoundryServiceInstance serviceInstance =
        getOsbServiceInstanceByRegion(region, serviceInstanceName);

    if (MANAGED_SERVICE_INSTANCE.name().equalsIgnoreCase(serviceInstance.getType())) {
      checkServiceShareable(serviceInstanceName, serviceInstance);
    }

    String serviceInstanceId = serviceInstance.getId();
    SharedTo sharedTo =
        safelyCall(() -> api.getShareServiceInstanceSpaceIdsByServiceInstanceId(serviceInstanceId))
            .orElseThrow(
                () ->
                    new CloudFoundryApiException(
                        "Could not fetch spaces to which '"
                            + serviceInstanceName
                            + "' has been shared"));
    Set<Map<String, String>> shareToIdsBody =
        shareToSpaces.stream()
            .map(space -> Collections.singletonMap("guid", space.getId()))
            .filter(idMap -> !sharedTo.getData().contains(idMap))
            .collect(toSet());

    if (shareToIdsBody.size() > 0) {
      safelyCall(
          () ->
              api.shareServiceInstanceToSpaceIds(
                  serviceInstanceId, new CreateSharedServiceInstances().setData(shareToIdsBody)));
    }

    return new ServiceInstanceResponse()
        .setServiceInstanceName(serviceInstanceName)
        .setType(SHARE)
        .setState(SUCCEEDED);
  }

  public ServiceInstanceResponse unshareServiceInstance(
      @Nullable String serviceInstanceName, @Nullable Set<String> unshareFromRegions) {
    Set<CloudFoundrySpace> unshareFromSpaces =
        vetUnshareServiceArgumentsAndGetSharingSpaces(serviceInstanceName, unshareFromRegions);

    unshareFromSpaces.forEach(
        space ->
            ofNullable(spaces.getServiceInstanceByNameAndSpace(serviceInstanceName, space))
                .map(
                    si ->
                        safelyCall(
                            () ->
                                api.unshareServiceInstanceFromSpaceId(si.getId(), space.getId()))));

    return new ServiceInstanceResponse()
        .setServiceInstanceName(serviceInstanceName)
        .setType(UNSHARE)
        .setState(SUCCEEDED);
  }

  @Nullable
  public CloudFoundryServiceInstance getServiceInstance(String region, String serviceInstanceName) {
    CloudFoundrySpace space =
        spaces
            .findSpaceByRegion(region)
            .orElseThrow(() -> new CloudFoundryApiException("Cannot find region '" + region + "'"));
    return ofNullable(getOsbServiceInstance(space, serviceInstanceName))
        .orElseGet(() -> getUserProvidedServiceInstance(space, serviceInstanceName));
  }

  @Nullable
  @VisibleForTesting
  CloudFoundryServiceInstance getOsbServiceInstance(
      CloudFoundrySpace space, @Nullable String serviceInstanceName) {
    return toCloudFoundryServiceInstance(
        getServiceInstanceByType(space, serviceInstanceName, "managed"), true);
  }

  @Nullable
  @VisibleForTesting
  CloudFoundryServiceInstance getUserProvidedServiceInstance(
      CloudFoundrySpace space, @Nullable String serviceInstanceName) {
    return toCloudFoundryServiceInstance(
        getServiceInstanceByType(space, serviceInstanceName, "user-provided"), false);
  }

  @Nullable
  private CloudFoundryServiceInstance toCloudFoundryServiceInstance(
      @Nullable ServiceInstance r, boolean managed) {
    if (r == null) {
      return null;
    }
    if (managed) {
      return CloudFoundryServiceInstance.builder()
          .serviceInstanceName(r.getName())
          .planId(r.getServicePlanGuid())
          .type(MANAGED_SERVICE_INSTANCE.toString())
          .status(r.getLastOperation() == null ? null : r.getLastOperation().getState())
          .lastOperationDescription(
              r.getLastOperation() == null ? null : r.getLastOperation().getDescription())
          .id(r.getGuid())
          .build();
    }
    return CloudFoundryServiceInstance.builder()
        .serviceInstanceName(r.getName())
        .type(USER_PROVIDED_SERVICE_INSTANCE.toString())
        .status(SUCCEEDED.toString())
        .id(r.getGuid())
        .build();
  }

  @Nullable
  private ServiceInstance getServiceInstanceByType(
      CloudFoundrySpace space, @Nullable String serviceInstanceName, String type) {
    if (isBlank(serviceInstanceName)) {
      throw new CloudFoundryApiException("Please specify a name for the service being sought");
    }

    List<ServiceInstance> serviceInstances =
        collectPages(
            "service instances by space and name",
            pg -> api.all(pg, serviceInstanceName, space.getId(), type));

    if (serviceInstances.isEmpty()) {
      return null;
    }

    if (serviceInstances.size() > 1) {
      throw new CloudFoundryApiException(
          serviceInstances.size()
              + " service instances found with name '"
              + serviceInstanceName
              + "' in space '"
              + space.getName()
              + "', but expected only 1");
    }

    return serviceInstances.get(0);
  }

  public ServiceInstanceResponse destroyServiceInstance(
      CloudFoundrySpace space, String serviceInstanceName) {
    List<ServiceInstance> matches =
        collectPages(
            "service instances by space and name",
            pg -> api.all(pg, serviceInstanceName, space.getId(), null));

    if (matches.isEmpty()) {
      return new ServiceInstanceResponse()
          .setServiceInstanceName(serviceInstanceName)
          .setType(DELETE)
          .setState(NOT_FOUND);
    }

    ServiceInstance serviceInstance = matches.get(0);
    List<ServiceCredentialBinding> serviceBindings =
        collectPages(
            "service bindings",
            pg -> api.getServiceBindings(pg, serviceInstance.getGuid(), null, "app"));
    if (!serviceBindings.isEmpty()) {
      throw new CloudFoundryApiException(
          "Unable to destroy service instance while "
              + serviceBindings.size()
              + " service binding(s) exist");
    }
    safelyCall(() -> api.destroyServiceInstance(serviceInstance.getGuid()));

    return new ServiceInstanceResponse()
        .setServiceInstanceName(serviceInstanceName)
        .setType(DELETE)
        .setState(IN_PROGRESS);
  }

  public ServiceInstanceResponse createServiceInstance(
      String newServiceInstanceName,
      String serviceName,
      String servicePlanName,
      Set<String> tags,
      Map<String, Object> parameters,
      boolean updatable,
      CloudFoundrySpace space) {
    List<CloudFoundryServicePlan> cloudFoundryServicePlans =
        findAllServicePlansByServiceName(serviceName);
    if (cloudFoundryServicePlans.isEmpty()) {
      throw new ResourceNotFoundException(
          "No plans available for service name '" + serviceName + "'");
    }

    String servicePlanId =
        cloudFoundryServicePlans.stream()
            .filter(plan -> plan.getName().equals(servicePlanName))
            .findAny()
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Service '"
                            + serviceName
                            + "' does not have a matching plan '"
                            + servicePlanName
                            + "'"))
            .getId();

    LastOperation.Type operationType = CREATE;
    List<ServiceInstance> existing =
        collectPages(
            "service instances",
            pg -> api.all(pg, newServiceInstanceName, space.getId(), "managed"));
    throwIfAmbiguous(existing, newServiceInstanceName, space);

    if (existing.isEmpty()) {
      safelyCall(
              () ->
                  api.createServiceInstance(
                      CreateServiceInstance.create(
                          newServiceInstanceName, space.getId(), servicePlanId, tags, parameters)))
          .orElseThrow(
              () ->
                  new CloudFoundryApiException(
                      "service instance '" + newServiceInstanceName + "' could not be created"));
    } else if (updatable) {
      operationType = UPDATE;
      ServiceInstance serviceInstance = existing.get(0);
      if (!servicePlanId.equals(serviceInstance.getServicePlanGuid())) {
        throw new CloudFoundryApiException(
            "A service with name '" + newServiceInstanceName + "' exists but has a different plan");
      }
      safelyCall(
          () ->
              api.updateServiceInstance(
                  serviceInstance.getGuid(),
                  CreateServiceInstance.forUpdate(
                      newServiceInstanceName, servicePlanId, tags, parameters)));
    }

    ServiceInstanceResponse response =
        new ServiceInstanceResponse()
            .setServiceInstanceName(newServiceInstanceName)
            .setType(operationType);
    response.setState(updatable ? IN_PROGRESS : SUCCEEDED);
    return response;
  }

  private void throwIfAmbiguous(
      List<ServiceInstance> existing, String serviceInstanceName, CloudFoundrySpace space) {
    if (existing.size() > 1) {
      throw new CloudFoundryApiException(
          existing.size()
              + " service instances found with name '"
              + serviceInstanceName
              + "' in space '"
              + space.getName()
              + "', but expected only 1");
    }
  }

  public ServiceInstanceResponse createUserProvidedServiceInstance(
      String newUserProvidedServiceInstanceName,
      String syslogDrainUrl,
      Set<String> tags,
      Map<String, Object> credentials,
      String routeServiceUrl,
      boolean updatable,
      CloudFoundrySpace space) {
    LastOperation.Type operationType = CREATE;
    List<ServiceInstance> existing =
        collectPages(
            "service instances",
            pg -> api.all(pg, newUserProvidedServiceInstanceName, space.getId(), "user-provided"));
    throwIfAmbiguous(existing, newUserProvidedServiceInstanceName, space);

    if (existing.isEmpty()) {
      safelyCall(
              () ->
                  api.createUserProvidedServiceInstance(
                      CreateUserProvidedServiceInstance.create(
                          newUserProvidedServiceInstanceName,
                          space.getId(),
                          syslogDrainUrl,
                          tags,
                          credentials,
                          routeServiceUrl)))
          .orElseThrow(
              () ->
                  new CloudFoundryApiException(
                      "service instance '"
                          + newUserProvidedServiceInstanceName
                          + "' could not be created"));
    } else if (updatable) {
      operationType = UPDATE;
      safelyCall(
          () ->
              api.updateUserProvidedServiceInstance(
                  existing.get(0).getGuid(),
                  CreateUserProvidedServiceInstance.forUpdate(
                      newUserProvidedServiceInstanceName,
                      syslogDrainUrl,
                      tags,
                      credentials,
                      routeServiceUrl)));
    }

    ServiceInstanceResponse response =
        new ServiceInstanceResponse()
            .setServiceInstanceName(newUserProvidedServiceInstanceName)
            .setType(operationType);
    response.setState(SUCCEEDED);
    return response;
  }
}
