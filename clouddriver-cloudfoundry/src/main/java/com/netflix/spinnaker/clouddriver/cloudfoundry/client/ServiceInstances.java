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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.repackaged.com.google.common.annotations.VisibleForTesting;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.ConfigService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.ServiceInstanceService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.ServiceInstanceResponse;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.*;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.CreateSharedServiceInstances;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClientUtils.collectPageResources;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClientUtils.safelyCall;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.ConfigFeatureFlag.ConfigFlag.SERVICE_INSTANCE_SHARING;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.LastOperation.State.*;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.LastOperation.Type.*;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.ServiceInstance.Type.MANAGED_SERVICE_INSTANCE;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.ServiceInstance.Type.USER_PROVIDED_SERVICE_INSTANCE;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.isBlank;

@RequiredArgsConstructor
public class ServiceInstances {
  private final ServiceInstanceService api;
  private final ConfigService configApi;
  private final Organizations orgs;
  private final Spaces spaces;

  public Void createServiceBindingsByName(CloudFoundryServerGroup cloudFoundryServerGroup, @Nullable List<String> serviceInstanceNames) {
    if (serviceInstanceNames != null && !serviceInstanceNames.isEmpty()) {
      List<String> serviceInstanceQuery = getServiceQueryParams(serviceInstanceNames, cloudFoundryServerGroup.getSpace());
      List<Resource<? extends AbstractServiceInstance>> serviceInstances = new ArrayList<>();
      serviceInstances.addAll(collectPageResources("service instances", pg -> api.all(pg, serviceInstanceQuery)));
      serviceInstances.addAll(collectPageResources("service instances", pg -> api.allUserProvided(pg, serviceInstanceQuery)));

      if (serviceInstances.size() != serviceInstanceNames.size()) {
        throw new CloudFoundryApiException("Number of service instances does not match the number of service names");
      }

      for (Resource<? extends AbstractServiceInstance> serviceInstance : serviceInstances) {
        api.createServiceBinding(new CreateServiceBinding(
          serviceInstance.getMetadata().getGuid(),
          cloudFoundryServerGroup.getId()
        ));
      }
    }
    return null;
  }

  private Resource<Service> findServiceByServiceName(String serviceName) {
    List<Resource<Service>> services = collectPageResources("services by name",
      pg -> api.findService(pg, singletonList("label:" + serviceName)));
    return Optional.ofNullable(services.get(0)).orElse(null);
  }

  private List<CloudFoundryServicePlan> findAllServicePlansByServiceName(String serviceName) {
    Resource<Service> service = findServiceByServiceName(serviceName);
    List<Resource<ServicePlan>> services = collectPageResources("service plans by id",
      pg -> api.findServicePlans(pg, singletonList("service_guid:" + service.getMetadata().getGuid())));

    return services.stream()
      .map(resource -> CloudFoundryServicePlan.builder()
        .name(resource.getEntity().getName())
        .id(resource.getMetadata().getGuid())
        .build())
      .collect(toList());
  }

  public List<CloudFoundryService> findAllServicesByRegion(String region) {
    CloudFoundrySpace space = getSpaceByRegionName(region);
    if (space == null) {
      return Collections.emptyList();
    }

    List<Resource<Service>> services = collectPageResources("all service", pg -> api.findServiceBySpaceId(space.getId(), pg, null));
    return services.stream()
      .map(serviceResource ->
        CloudFoundryService.builder()
          .name(serviceResource.getEntity().getLabel())
          .servicePlans(findAllServicePlansByServiceName(serviceResource.getEntity().getLabel()))
          .build())
      .collect(toList());
  }

  private CloudFoundrySpace getSpaceByRegionName(String region) {
    CloudFoundrySpace space = CloudFoundrySpace.fromRegion(region);
    Optional<CloudFoundryOrganization> org = orgs.findByName(space.getOrganization().getName());

    return org.map(cfOrg -> spaces.findByName(cfOrg.getId(), space.getName())).orElse(null);
  }

  // Visible for testing
  CloudFoundryServiceInstance getOsbServiceInstanceByRegion(String region, String serviceInstanceName) {
    CloudFoundrySpace space = Optional.ofNullable(getSpaceByRegionName(region))
      .orElseThrow(() -> new CloudFoundryApiException("Cannot find region '" + region + "'"));
    return Optional
      .ofNullable(getOsbServiceInstance(space, serviceInstanceName))
      .orElseThrow(() -> new CloudFoundryApiException("Cannot find service '" + serviceInstanceName + "' in region '" + space.getRegion() + "'"));
  }

  private Set<CloudFoundrySpace> vetSharingOfServicesArgumentsAndGetSharingSpaces(
    String sharedFromRegion,
    @Nullable String serviceInstanceName,
    @Nullable Set<String> sharingRegions,
    String gerund) {
    if (isBlank(serviceInstanceName)) {
      throw new CloudFoundryApiException("Please specify a name for the " + gerund + " service instance");
    }
    sharingRegions = Optional.ofNullable(sharingRegions).orElse(Collections.emptySet());
    if (sharingRegions.size() == 0) {
      throw new CloudFoundryApiException("Please specify a list of regions for " + gerund + " '" + serviceInstanceName + "'");
    }

    return sharingRegions.stream()
      .map(r -> {
        if (sharedFromRegion.equals(r)) {
          throw new CloudFoundryApiException("Cannot specify 'org > space' as any of the " + gerund + " regions");
        }
        return Optional.ofNullable(getSpaceByRegionName(r))
          .orElseThrow(() -> new CloudFoundryApiException("Cannot find region '" + r + "' for " + gerund));
      })
      .collect(toSet());
  }

  // Visible for testing
  Set<CloudFoundrySpace> vetUnshareServiceArgumentsAndGetSharingSpaces(
    @Nullable String serviceInstanceName,
    @Nullable Set<String> sharingRegions) {
    return vetSharingOfServicesArgumentsAndGetSharingSpaces("", serviceInstanceName, sharingRegions, "unsharing");
  }

  // Visible for testing
  Set<CloudFoundrySpace> vetShareServiceArgumentsAndGetSharingSpaces(
    @Nullable String sharedFromRegion,
    @Nullable String serviceInstanceName,
    @Nullable Set<String> sharingRegions) {
    if (isBlank(sharedFromRegion)) {
      throw new CloudFoundryApiException("Please specify a region for the sharing service instance");
    }
    return vetSharingOfServicesArgumentsAndGetSharingSpaces(sharedFromRegion, serviceInstanceName, sharingRegions, "sharing");
  }

  // Visible for testing
  Void checkServiceShareable(String serviceInstanceName, CloudFoundryServiceInstance serviceInstance) {
    ConfigFeatureFlag featureFlag =
      Optional.ofNullable(configApi.getConfigFeatureFlags())
        .orElse(Collections.emptySet())
        .stream()
        .filter(it -> it.getName() == SERVICE_INSTANCE_SHARING)
        .findFirst()
        .orElseThrow(() -> new CloudFoundryApiException("'service_instance_sharing' flag must be enabled in order to share services"));
    if (!featureFlag.isEnabled()) {
      throw new CloudFoundryApiException("'service_instance_sharing' flag must be enabled in order to share services");
    }
    ServicePlan plan = Optional.ofNullable(api.findServicePlanByServicePlanId(serviceInstance.getPlanId()))
      .map(Resource::getEntity)
      .orElseThrow(() -> new CloudFoundryApiException("The service plan for 'new-service-plan-name' was not found"));
    String extraString = Optional.ofNullable(api.findServiceByServiceId(plan.getServiceGuid()))
      .map(Resource::getEntity)
      .map(s -> Optional.ofNullable(s.getExtra())
        .orElseThrow(() -> new CloudFoundryApiException("The service broker must be configured as 'shareable' in order to share services")))
      .orElseThrow(() -> new CloudFoundryApiException("The service broker for '" + serviceInstanceName + "' was not found"));

    boolean isShareable;
    try {
      isShareable = !StringUtils.isEmpty(extraString) &&
        new ObjectMapper().readValue(extraString, Map.class).get("shareable") == Boolean.TRUE;
    } catch (IOException e) {
      throw new CloudFoundryApiException(e);
    }

    if (!isShareable) {
      throw new CloudFoundryApiException("The service broker must be configured as 'shareable' in order to share services");
    }

    return null;
  }

  public ServiceInstanceResponse shareServiceInstance(@Nullable String region, @Nullable String serviceInstanceName, @Nullable Set<String> shareToRegions) {
    Set<CloudFoundrySpace> shareToSpaces = vetShareServiceArgumentsAndGetSharingSpaces(region, serviceInstanceName, shareToRegions);
    CloudFoundryServiceInstance serviceInstance = getOsbServiceInstanceByRegion(region, serviceInstanceName);

    if (MANAGED_SERVICE_INSTANCE.name().equalsIgnoreCase(serviceInstance.getType())) {
      checkServiceShareable(serviceInstanceName, serviceInstance);
    }

    String serviceInstanceId = serviceInstance.getId();
    SharedTo sharedTo = safelyCall(() -> api.getShareServiceInstanceSpaceIdsByServiceInstanceId(serviceInstanceId))
      .orElseThrow(() -> new CloudFoundryApiException("Could not fetch spaces to which '" + serviceInstanceName + "' has been shared"));
    Set<Map<String, String>> shareToIdsBody = shareToSpaces.stream()
      .map(space -> Collections.singletonMap("guid", space.getId()))
      .filter(idMap -> !sharedTo.getData().contains(idMap))
      .collect(toSet());

    if (shareToIdsBody.size() > 0) {
      safelyCall(() -> api.shareServiceInstanceToSpaceIds(serviceInstanceId, new CreateSharedServiceInstances().setData(shareToIdsBody)));
    }

    return new ServiceInstanceResponse()
      .setServiceInstanceName(serviceInstanceName)
      .setType(SHARE)
      .setState(SUCCEEDED);
  }

  public ServiceInstanceResponse unshareServiceInstance(
    @Nullable String serviceInstanceName,
    @Nullable Set<String> unshareFromRegions) {
    Set<CloudFoundrySpace> unshareFromSpaces = vetUnshareServiceArgumentsAndGetSharingSpaces(serviceInstanceName, unshareFromRegions);

    unshareFromSpaces
      .forEach(s -> Optional.ofNullable(spaces.getSpaceSummaryById(s.getId()))
        .map(SpaceSummary::getServices)
        .ifPresent(set -> set.stream()
          .filter(si -> serviceInstanceName.equals(si.getName()))
          .forEach(si -> safelyCall(() -> api.unshareServiceInstanceFromSpaceId(si.getGuid(), s.getId())))
        ));

    return new ServiceInstanceResponse()
      .setServiceInstanceName(serviceInstanceName)
      .setType(UNSHARE)
      .setState(SUCCEEDED);
  }

  @Nullable
  public CloudFoundryServiceInstance getServiceInstance(String region, String serviceInstanceName) {
    CloudFoundrySpace space = Optional.ofNullable(getSpaceByRegionName(region))
      .orElseThrow(() -> new CloudFoundryApiException("Cannot find region '" + region + "'"));
    Supplier<CloudFoundryServiceInstance> si = () -> Optional.ofNullable(getOsbServiceInstance(space, serviceInstanceName))
      .orElse(null);

    Supplier<CloudFoundryServiceInstance> up = () -> Optional.ofNullable(getUserProvidedServiceInstance(space, serviceInstanceName))
      .orElse(null);

    return Optional.ofNullable(si.get()).orElseGet(up);
  }

  @Nullable
  @VisibleForTesting
  CloudFoundryServiceInstance getOsbServiceInstance(CloudFoundrySpace space, @Nullable String serviceInstanceName) {
    return Optional.ofNullable(getServiceInstance(api::all, space, serviceInstanceName))
      .map(r -> CloudFoundryServiceInstance.builder()
        .serviceInstanceName(r.getEntity().getName())
        .planId(r.getEntity().getServicePlanGuid())
        .type(r.getEntity().getType().toString())
        .status(r.getEntity().getLastOperation().getState().toString())
        .id(r.getMetadata().getGuid())
        .build()
      )
      .orElse(null);
  }

  @Nullable
  @VisibleForTesting
  CloudFoundryServiceInstance getUserProvidedServiceInstance(CloudFoundrySpace space, @Nullable String serviceInstanceName) {
    return Optional.ofNullable(getServiceInstance(api::allUserProvided, space, serviceInstanceName))
      .map(r -> CloudFoundryServiceInstance.builder()
        .serviceInstanceName(r.getEntity().getName())
        .type(USER_PROVIDED_SERVICE_INSTANCE.toString())
        .status(SUCCEEDED.toString())
        .id(r.getMetadata().getGuid())
        .build()
      )
      .orElse(null);
  }

  @Nullable
  private <T> Resource<T>
  getServiceInstance(BiFunction<Integer, List<String>, Page<T>> func, CloudFoundrySpace space, @Nullable String serviceInstanceName) {
    if (isBlank(serviceInstanceName)) {
      throw new CloudFoundryApiException("Please specify a name for the service being sought");
    }

    List<Resource<T>> serviceInstances = collectPageResources("service instances by space and name",
      pg -> func.apply(pg, getServiceQueryParams(Collections.singletonList(serviceInstanceName), space)));

    if (serviceInstances.isEmpty()) {
      return null;
    }

    if (serviceInstances.size() > 1) {
      throw new CloudFoundryApiException(serviceInstances.size() + " service instances found with name '" +
        serviceInstanceName + "' in space '" + space.getName() + "', but expected only 1");
    }

    return serviceInstances.get(0);
  }

  public ServiceInstanceResponse destroyServiceInstance(CloudFoundrySpace space, String serviceInstanceName) {
    CloudFoundryServiceInstance serviceInstance = getOsbServiceInstance(space, serviceInstanceName);
    if (serviceInstance != null) {
      String serviceInstanceId = serviceInstance.getId();
      destroyServiceInstance(
        pg -> api.getBindingsForServiceInstance(serviceInstanceId, pg, null),
        () -> api.destroyServiceInstance(serviceInstanceId));
      return new ServiceInstanceResponse()
        .setServiceInstanceName(serviceInstanceName)
        .setType(DELETE)
        .setState(IN_PROGRESS);
    } else {
      CloudFoundryServiceInstance userProvidedServiceInstance = getUserProvidedServiceInstance(space, serviceInstanceName);
      if (userProvidedServiceInstance == null) {
        throw new CloudFoundryApiException("No service instances with name '" + serviceInstanceName + "' found in space " + space.getName());
      }
      String userProvidedServiceInstanceId = userProvidedServiceInstance.getId();
      destroyServiceInstance(
        pg -> api.getBindingsForUserProvidedServiceInstance(userProvidedServiceInstanceId, pg, null),
        () -> api.destroyUserProvidedServiceInstance(userProvidedServiceInstanceId));
      return new ServiceInstanceResponse()
        .setServiceInstanceName(serviceInstanceName)
        .setType(DELETE)
        .setState(NOT_FOUND);
    }
  }

  private void destroyServiceInstance(Function<Integer, Page<ServiceBinding>> fetchPage, Runnable delete) {
    List<Resource<ServiceBinding>> serviceBindings = collectPageResources("service bindings", fetchPage);
    if (!serviceBindings.isEmpty()) {
      throw new CloudFoundryApiException("Unable to destroy service instance while " + serviceBindings.size() + " service binding(s) exist");
    }
    safelyCall(delete::run);
  }

  public ServiceInstanceResponse createServiceInstance(
    String newServiceInstanceName,
    String serviceName,
    String servicePlanName,
    Set<String> tags,
    Map<String, Object> parameters,
    boolean updatable,
    CloudFoundrySpace space) {
    List<CloudFoundryServicePlan> cloudFoundryServicePlans = findAllServicePlansByServiceName(serviceName);
    if (cloudFoundryServicePlans.isEmpty()) {
      throw new ResourceNotFoundException("No plans available for service name '" + serviceName + "'");
    }

    String servicePlanId = cloudFoundryServicePlans.stream()
      .filter(plan -> plan.getName().equals(servicePlanName))
      .findAny()
      .orElseThrow(() -> new ResourceNotFoundException("Service '" + serviceName + "' does not have a matching plan '" + servicePlanName + "'"))
      .getId();

    CreateServiceInstance command = new CreateServiceInstance();
    command.setName(newServiceInstanceName);
    command.setSpaceGuid(space.getId());
    command.setServicePlanGuid(servicePlanId);
    command.setTags(tags);
    command.setParameters(parameters);

    ServiceInstanceResponse response = createServiceInstance(
      command,
      api::createServiceInstance,
      api::updateServiceInstance,
      api::all,
      c -> getOsbServiceInstance(space, c.getName()),
      (c, r) -> {
        if (!r.getPlanId().equals(c.getServicePlanGuid())) {
          throw new CloudFoundryApiException("A service with name '" + c.getName() + "' exists but has a different plan");
        }
      },
      updatable,
      space
    );

    response.setState(updatable ? IN_PROGRESS : SUCCEEDED);
    return response;
  }

  public ServiceInstanceResponse createUserProvidedServiceInstance(
    String newUserProvidedServiceInstanceName,
    String syslogDrainUrl,
    Set<String> tags,
    Map<String, Object> credentials,
    String routeServiceUrl,
    boolean updatable,
    CloudFoundrySpace space) {
    CreateUserProvidedServiceInstance command = new CreateUserProvidedServiceInstance();
    command.setName(newUserProvidedServiceInstanceName);
    command.setSyslogDrainUrl(syslogDrainUrl);
    command.setTags(tags);
    command.setCredentials(credentials);
    command.setRouteServiceUrl(routeServiceUrl);
    command.setSpaceGuid(space.getId());

    ServiceInstanceResponse response = createServiceInstance(
      command,
      api::createUserProvidedServiceInstance,
      api::updateUserProvidedServiceInstance,
      api::allUserProvided,
      c -> getUserProvidedServiceInstance(space, c.getName()),
      (c, r) -> {
      },
      updatable,
      space
    );

    response.setState(SUCCEEDED);
    return response;
  }

  private <T extends AbstractCreateServiceInstance,
    S extends AbstractServiceInstance> ServiceInstanceResponse
  createServiceInstance(T command,
                        Function<T, Resource<S>> create,
                        BiFunction<String, T, Resource<S>> update,
                        BiFunction<Integer, List<String>, Page<S>> getAllServices,
                        Function<T, CloudFoundryServiceInstance> getServiceInstance,
                        BiConsumer<T, CloudFoundryServiceInstance> updateValidation,
                        boolean updatable,
                        CloudFoundrySpace space) {
    LastOperation.Type operationType;
    List<String> serviceInstanceQuery = getServiceQueryParams(Collections.singletonList(command.getName()), space);
    List<Resource<? extends AbstractServiceInstance>> serviceInstances = new ArrayList<>();
    serviceInstances.addAll(collectPageResources("service instances", pg -> getAllServices.apply(pg, serviceInstanceQuery)));

    operationType = CREATE;
    if (serviceInstances.size() == 0) {
      safelyCall(() -> create.apply(command)).map(res -> res.getMetadata().getGuid())
        .orElseThrow(() -> new CloudFoundryApiException("service instance '" + command.getName() + "' could not be created"));
    } else if (updatable) {
      operationType = UPDATE;
      serviceInstances.stream()
        .findFirst()
        .map(r -> r.getMetadata().getGuid())
        .orElseThrow(() -> new CloudFoundryApiException("Service instance '" + command.getName() + "' not found"));
      CloudFoundryServiceInstance serviceInstance = getServiceInstance.apply(command);
      if (serviceInstance == null) {
        throw new CloudFoundryApiException("No service instances with name '" + command.getName() + "' found in space " + space.getName());
      }
      updateValidation.accept(command, serviceInstance);
      safelyCall(() -> update.apply(serviceInstance.getId(), command));
    }

    return new ServiceInstanceResponse()
      .setServiceInstanceName(command.getName())
      .setType(operationType);
  }

  private static List<String> getServiceQueryParams(List<String> serviceNames, CloudFoundrySpace space) {
    return Arrays.asList(
      serviceNames.size() == 1 ? "name:" + serviceNames.get(0) : "name IN " + String.join(",", serviceNames),
      "organization_guid:" + space.getOrganization().getId(),
      "space_guid:" + space.getId()
    );
  }
}
