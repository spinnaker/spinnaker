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

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.ServiceInstanceService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.*;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.*;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClientUtils.collectPageResources;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClientUtils.safelyCall;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.ErrorDescription.Code.SERVICE_ALREADY_EXISTS;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.LastOperation.State.FAILED;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.LastOperation.State.SUCCEEDED;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.LastOperation.Type.CREATE;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.LastOperation.Type.DELETE;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.LastOperation.Type.UPDATE;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;

@AllArgsConstructor
@RequiredArgsConstructor
public class ServiceInstances {
  private final ServiceInstanceService api;
  private final Organizations orgs;
  private final Spaces spaces;
  private Duration pollingInterval = Duration.ofSeconds(10);

  public void createServiceBindingsByName(CloudFoundryServerGroup cloudFoundryServerGroup, @Nullable List<String> serviceNames) throws CloudFoundryApiException {
    if (serviceNames != null && !serviceNames.isEmpty()) {
      String spaceGuid = cloudFoundryServerGroup.getSpace().getId();
      String query = "name IN " + String.join(",", serviceNames);

      List<Resource<ServiceInstance>> serviceInstances = collectPageResources("service instances", pg -> api.all(pg, spaceGuid, query));

      if (serviceInstances.size() != serviceNames.size()) {
        throw new CloudFoundryApiException("Number of service instances does not match the number of service names");
      }

      for (Resource<ServiceInstance> serviceInstance : serviceInstances) {
        api.createServiceBinding(new CreateServiceBinding(
          serviceInstance.getMetadata().getGuid(),
          cloudFoundryServerGroup.getId()
        ));
      }
    }
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

  // package-level for testing visibility
  Resource<ServiceInstance> getServiceInstance(CloudFoundrySpace space, @Nullable String serviceInstanceName) throws CloudFoundryApiException {
    if (isBlank(serviceInstanceName)) {
      throw new CloudFoundryApiException("Please specify a name for the service being sought");
    }
    List<String> queryParams = singletonList("name IN " + serviceInstanceName);
    List<Resource<ServiceInstance>> services = collectPageResources("service instances by space and name",
      pg -> api.findAllServiceInstancesBySpaceId(space.getId(), pg, queryParams));

    if (services.isEmpty()) {
      throw new CloudFoundryApiException("No service instances with name '" + serviceInstanceName + "' found in space " + space.getName());
    }

    if (services.size() > 1) {
      throw new CloudFoundryApiException(services.size() + " service instances found with name '" +
        serviceInstanceName + "' in space " + space.getName() + ", but expected only 1");
    }

    return services.get(0);
  }

  public void destroyServiceInstance(CloudFoundrySpace space, String serviceInstanceName, Duration timeout) throws CloudFoundryApiException {
    Resource<ServiceInstance> serviceInstance = getServiceInstance(space, serviceInstanceName);
    String serviceInstanceId = serviceInstance.getMetadata().getGuid();
    List<Resource<ServiceBinding>> serviceBindings = collectPageResources("service bindings",
      pg -> api.getBindingsForServiceInstance(serviceInstanceId, pg, null));

    if (!serviceBindings.isEmpty()) {
      throw new CloudFoundryApiException("Unable to destroy service instance while " + serviceBindings.size() + " service binding(s) exist");
    }

    safelyCall(() -> api.destroyServiceInstance(serviceInstanceId));
    pollServiceInstanceStatus(serviceInstanceName, serviceInstanceId, DELETE, timeout);
  }

  public void createServiceInstance(String newServiceInstanceName, String serviceName, String servicePlanName,
                                    Set<String> tags, Map<String, Object> parameters, CloudFoundrySpace space,
                                    Duration timeout)
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

    LastOperation.Type type = CREATE;
    String guid;
    try {
      guid = safelyCall(() -> api.createServiceInstance(command)).map(res -> res.getMetadata().getGuid())
        .orElseThrow(() -> new CloudFoundryApiException("Service instance '" + newServiceInstanceName + "' not found"));
    } catch (CloudFoundryApiException cfe) {
      if (cfe.getErrorCode() == SERVICE_ALREADY_EXISTS) {
        Resource<ServiceInstance> serviceInstanceResource = getServiceInstance(space, newServiceInstanceName);
        if (!serviceInstanceResource.getEntity().getServicePlanGuid().equals(command.getServicePlanGuid())) {
          throw new CloudFoundryApiException("A service with name '" + serviceName + "' exists but has a different plan");
        }

        guid = safelyCall(() -> api.updateServiceInstance(serviceInstanceResource.getMetadata().getGuid(), command))
          .map(res -> res.getMetadata().getGuid())
          .orElseThrow(() -> new CloudFoundryApiException("Service instance '" + newServiceInstanceName + "' not found"));
        type = UPDATE;
      } else {
        throw cfe;
      }
    }

    pollServiceInstanceStatus(newServiceInstanceName, guid, type, timeout);
  }

  // package-level for testing visibility
  void pollServiceInstanceStatus(String serviceInstanceName, String guid, LastOperation.Type type, Duration timeout) {
    RetryConfig retryConfig = RetryConfig.custom()
      .waitDuration(pollingInterval)
      .maxAttempts((int) (timeout.toMillis() / pollingInterval.toMillis()))
      .retryExceptions(OperationInProgressException.class)
      .build();

    AtomicReference<LastOperation> lastOperation = new AtomicReference<>();
    try {
      Retry.of("async-service-operation", retryConfig).executeCallable(() -> {
        LastOperation operation = safelyCall(() -> api.getServiceInstanceById(guid))
          .map(res -> res.getEntity().getLastOperation())
          .orElse(type == DELETE ? new LastOperation().setType(DELETE).setState(SUCCEEDED) : new LastOperation().setType(type).setState(FAILED));
        lastOperation.set(operation);

        switch (operation.getState()) {
          case FAILED:
            throw new CloudFoundryApiException("Service instance '" + serviceInstanceName + "' " + type + " failed");
          case IN_PROGRESS:
            throw new OperationInProgressException();
          case SUCCEEDED:
            break;
        }
        return operation;
      });
    } catch (CloudFoundryApiException e) {
      throw e;
    } catch (OperationInProgressException ignored) {
      throw new CloudFoundryApiException("Service instance '" + serviceInstanceName + "' " + type + " did not complete");
    } catch (Exception unknown) {
      throw new CloudFoundryApiException(unknown);
    }
  }

  private static class OperationInProgressException extends RuntimeException {
  }
}
