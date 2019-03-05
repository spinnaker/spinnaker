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

import com.google.api.client.repackaged.com.google.common.annotations.VisibleForTesting;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.ServiceInstanceService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.ServiceInstanceResponse;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.*;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.*;
import lombok.RequiredArgsConstructor;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClientUtils.collectPageResources;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClientUtils.safelyCall;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.LastOperation.State.*;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.LastOperation.Type.*;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;

@RequiredArgsConstructor
public class ServiceInstances {
  private static final String SPINNAKER_VERSION_V_D_3 = "spinnakerVersion-v\\d{3}";
  private final ServiceInstanceService api;
  private final Organizations orgs;
  private final Spaces spaces;

  public Void createServiceBindingsByName(CloudFoundryServerGroup cloudFoundryServerGroup, @Nullable List<String> serviceInstanceNames) throws CloudFoundryApiException {
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

  @Nullable
  public CloudFoundryServiceInstance getServiceInstance(String region, @Nullable String serviceInstanceName) throws CloudFoundryApiException {
    CloudFoundrySpace space = getSpaceByRegionName(region);
    Supplier<CloudFoundryServiceInstance> si = () -> Optional.ofNullable(getServiceInstance(space, serviceInstanceName))
      .map(Resource::getEntity)
      .map(e -> CloudFoundryServiceInstance.builder()
        .serviceInstanceName(serviceInstanceName)
        .status(e.getLastOperation().getState().toString())
        .build()
      )
      .orElse(null);

    Supplier<CloudFoundryServiceInstance> up = () -> Optional.ofNullable(getUserProvidedServiceInstance(space, serviceInstanceName))
      .map(Resource::getEntity)
      .map(e -> CloudFoundryServiceInstance.builder()
        .serviceInstanceName(serviceInstanceName)
        .status(SUCCEEDED.toString())
        .build()
      )
      .orElse(null);

    return Optional.ofNullable(si.get()).orElseGet(up);
  }

  @Nullable
  @VisibleForTesting
  Resource<ServiceInstance> getServiceInstance(CloudFoundrySpace space, @Nullable String serviceInstanceName) throws CloudFoundryApiException {
    if (isBlank(serviceInstanceName)) {
      throw new CloudFoundryApiException("Please specify a name for the service being sought");
    }
    List<Resource<ServiceInstance>> serviceInstances = collectPageResources("service instances by space and name",
      pg -> api.all(pg, getServiceQueryParams(Collections.singletonList(serviceInstanceName), space)));

    return getValidServiceInstance(space, serviceInstanceName, serviceInstances);
  }

  @Nullable
  private Resource<UserProvidedServiceInstance> getUserProvidedServiceInstance(CloudFoundrySpace space, @Nullable String serviceInstanceName) throws CloudFoundryApiException {
    if (isBlank(serviceInstanceName)) {
      throw new CloudFoundryApiException("Please specify a name for the service being sought");
    }
    List<Resource<UserProvidedServiceInstance>> services = collectPageResources("service instances by space and name",
      pg -> api.allUserProvided(pg, getServiceQueryParams(Collections.singletonList(serviceInstanceName), space)));

    return getValidServiceInstance(space, serviceInstanceName, services);
  }

  private <T> Resource<T> getValidServiceInstance(CloudFoundrySpace space, @Nullable String serviceInstanceName, List<Resource<T>> serviceInstances) {
    if (serviceInstances.isEmpty()) {
      return null;
    }

    if (serviceInstances.size() > 1) {
      throw new CloudFoundryApiException(serviceInstances.size() + " service instances found with name '" +
        serviceInstanceName + "' in space " + space.getName() + ", but expected only 1");
    }

    return serviceInstances.get(0);
  }

  public ServiceInstanceResponse destroyServiceInstance(CloudFoundrySpace space, String serviceInstanceName) throws CloudFoundryApiException {
    Resource<ServiceInstance> serviceInstance = getServiceInstance(space, serviceInstanceName);
    if (serviceInstance != null) {
      String serviceInstanceId = serviceInstance.getMetadata().getGuid();
      destroyServiceInstance(
        pg -> api.getBindingsForServiceInstance(serviceInstanceId, pg, null),
        () -> api.destroyServiceInstance(serviceInstanceId));
      return new ServiceInstanceResponse()
        .setServiceInstanceId(serviceInstanceId)
        .setServiceInstanceName(serviceInstanceName)
        .setType(DELETE)
        .setState(IN_PROGRESS);
    } else {
      Resource<UserProvidedServiceInstance> userProvidedServiceInstance = getUserProvidedServiceInstance(space, serviceInstanceName);
      if (userProvidedServiceInstance == null) {
        throw new CloudFoundryApiException("No service instances with name '" + serviceInstanceName + "' found in space " + space.getName());
      }
      String userProvidedServiceInstanceId = userProvidedServiceInstance.getMetadata().getGuid();
      destroyServiceInstance(
        pg -> api.getBindingsForUserProvidedServiceInstance(userProvidedServiceInstanceId, pg, null),
        () -> api.destroyUserProvidedServiceInstance(userProvidedServiceInstanceId));
      return new ServiceInstanceResponse()
        .setServiceInstanceId(userProvidedServiceInstanceId)
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
    CloudFoundrySpace space)
    throws CloudFoundryApiException, ResourceNotFoundException {
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
      c -> getServiceInstance(space, c.getName()),
      (c, r) -> {
        if (!r.getEntity().getServicePlanGuid().equals(c.getServicePlanGuid())) {
          throw new CloudFoundryApiException("A service with name '" + c.getName() + "' exists but has a different plan");
        }
      },
      space
    );

    response.setState(IN_PROGRESS);
    return response;
  }

  public ServiceInstanceResponse createUserProvidedServiceInstance(
    String newUserProvidedServiceInstanceName,
    String syslogDrainUrl,
    Set<String> tags,
    Map<String, Object> credentials,
    String routeServiceUrl,
    CloudFoundrySpace space) throws CloudFoundryApiException, ResourceNotFoundException {
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
      c -> getUserProvidedServiceInstance(space, c.getName()),
      (c, r) -> {
      },
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
                        Function<T, Resource<S>> getServiceInstance,
                        BiConsumer<T, Resource<S>> updateValidation,
                        CloudFoundrySpace space) {
    String serviceInstanceId;
    LastOperation.Type operationType;
    List<String> serviceInstanceQuery = getServiceQueryParams(Collections.singletonList(command.getName()), space);
    List<Resource<? extends AbstractServiceInstance>> serviceInstances = new ArrayList<>();
    serviceInstances.addAll(collectPageResources("service instances", pg -> api.all(pg, serviceInstanceQuery)));
    serviceInstances.addAll(collectPageResources("service instances", pg -> api.allUserProvided(pg, serviceInstanceQuery)));

    if (serviceInstances.size() == 0) {
      operationType = CREATE;
      serviceInstanceId = safelyCall(() -> create.apply(command)).map(res -> res.getMetadata().getGuid())
        .orElseThrow(() -> new CloudFoundryApiException("service instance '" + command.getName() + "' could not be created"));
    } else {
      operationType = UPDATE;
      serviceInstanceId = serviceInstances.stream()
        .findFirst()
        .map(r -> r.getMetadata().getGuid())
        .orElseThrow(() -> new CloudFoundryApiException("Service instance '" + command.getName() + "' not found"));
      String existingServiceInstanceVersionTag = serviceInstances.stream()
        .findFirst().map(s -> s.getEntity().getTags())
        .orElse(new HashSet<>())
        .stream()
        .filter(t -> t.matches(SPINNAKER_VERSION_V_D_3))
        .min(Comparator.reverseOrder())
        .orElse("");
      String newServiceInstanceVersionTag = Optional.ofNullable(command.getTags())
        .orElse(Collections.emptySet())
        .stream()
        .filter(t -> t.matches(SPINNAKER_VERSION_V_D_3))
        .min(Comparator.reverseOrder())
        .orElse("");
      if (newServiceInstanceVersionTag.isEmpty() || !existingServiceInstanceVersionTag.equals(newServiceInstanceVersionTag)) {
        Resource<S> serviceInstance = getServiceInstance.apply(command);
        if (serviceInstance == null) {
          throw new CloudFoundryApiException("No service instances with name '" + command.getName() + "' found in space " + space.getName());
        }
        updateValidation.accept(command, serviceInstance);
        safelyCall(() -> update.apply(serviceInstance.getMetadata().getGuid(), command));
      }
    }

    return new ServiceInstanceResponse()
      .setServiceInstanceId(serviceInstanceId)
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
