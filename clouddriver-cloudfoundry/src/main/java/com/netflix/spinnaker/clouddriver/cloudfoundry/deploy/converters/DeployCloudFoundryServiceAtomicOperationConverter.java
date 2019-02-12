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

package com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.converters;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.google.common.annotations.VisibleForTesting;
import com.netflix.spinnaker.clouddriver.artifacts.ArtifactCredentialsRepository;
import com.netflix.spinnaker.clouddriver.cloudfoundry.CloudFoundryOperation;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.DeployCloudFoundryServiceDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.ops.DeployCloudFoundryServiceAtomicOperation;
import com.netflix.spinnaker.clouddriver.cloudfoundry.servicebroker.AtomicOperations;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

@CloudFoundryOperation(AtomicOperations.DEPLOY_SERVICE)
@Component
public class DeployCloudFoundryServiceAtomicOperationConverter extends AbstractCloudFoundryAtomicOperationConverter {
  private final ArtifactCredentialsRepository credentialsRepository;

  public DeployCloudFoundryServiceAtomicOperationConverter(ArtifactCredentialsRepository credentialsRepository) {
    this.credentialsRepository = credentialsRepository;
  }

  @Override
  public AtomicOperation convertOperation(Map input) {
    return new DeployCloudFoundryServiceAtomicOperation(convertDescription(input));
  }

  @Override
  public DeployCloudFoundryServiceDescription convertDescription(Map input) {
    DeployCloudFoundryServiceDescription converted = getObjectMapper().convertValue(input, DeployCloudFoundryServiceDescription.class);
    converted.setClient(getClient(input));
    converted.setSpace(findSpace(converted.getRegion(), converted.getClient())
      .orElseThrow(() -> new IllegalArgumentException("Unable to find space '" + converted.getRegion() + "'.")));
    Map manifest = (Map) input.get("manifest");
    String type = manifest.get("type").toString();
    switch (type) {
      case "direct":
        DeployCloudFoundryServiceDescription.ServiceAttributes attrs = getObjectMapper().convertValue(manifest, DeployCloudFoundryServiceDescription.ServiceAttributes.class);
        if (manifest.get("parameters") != null) {
          attrs.setParameterMap(parseParameters(manifest.get("parameters").toString()));
        }
        converted.setServiceAttributes(attrs);
        break;
      case "artifact":
        downloadAndProcessManifest(manifest, credentialsRepository, myMap -> converted.setServiceAttributes(convertManifest(myMap)));
        break;
      case "userProvided":
        converted.setServiceType("userProvided");
        DeployCloudFoundryServiceDescription.UserProvidedServiceAttributes userProvidedAttrs = getObjectMapper().convertValue(manifest, DeployCloudFoundryServiceDescription.UserProvidedServiceAttributes.class);
        if (manifest.get("credentials") != null) {
          userProvidedAttrs.setCredentialsMap(parseParameters(manifest.get("credentials").toString()));
        }
        converted.setUserProvidedServiceAttributes(userProvidedAttrs);
        break;
      case "userProvidedArtifact":
        converted.setServiceType("userProvided");
        downloadAndProcessManifest(manifest, credentialsRepository, myMap -> converted.setUserProvidedServiceAttributes(convertUserProvidedServiceManifest(myMap)));
        break;
    }
    return converted;
  }

  @VisibleForTesting
  DeployCloudFoundryServiceDescription.ServiceAttributes convertManifest(Map manifestMap) {
    ServiceManifest manifest = new ObjectMapper()
      .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .convertValue(manifestMap, new TypeReference<ServiceManifest>() {
      });
    if (manifest.getService() == null) {
      throw new IllegalArgumentException("Manifest is missing the service");
    } else if (manifest.getServiceInstanceName() == null) {
      throw new IllegalArgumentException("Manifest is missing the service instance name");
    } else if(manifest.getServicePlan() == null) {
      throw new IllegalArgumentException("Manifest is missing the service plan");
    }
    DeployCloudFoundryServiceDescription.ServiceAttributes attrs = new DeployCloudFoundryServiceDescription.ServiceAttributes();
    attrs.setService(manifest.getService());
    attrs.setServiceInstanceName(manifest.getServiceInstanceName());
    attrs.setServicePlan(manifest.getServicePlan());
    attrs.setTags(manifest.getTags());
    attrs.setParameterMap(parseParameters(manifest.getParameters()));
    return attrs;
  }

  @VisibleForTesting
  DeployCloudFoundryServiceDescription.UserProvidedServiceAttributes convertUserProvidedServiceManifest(Map manifestMap) {
    UserProvidedServiceManifest manifest = new ObjectMapper()
      .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .convertValue(manifestMap, new TypeReference<UserProvidedServiceManifest>() {
      });
    if (manifest.getServiceInstanceName() == null) {
      throw new IllegalArgumentException("Manifest is missing the service name");
    }
    DeployCloudFoundryServiceDescription.UserProvidedServiceAttributes attrs = new DeployCloudFoundryServiceDescription.UserProvidedServiceAttributes();
    attrs.setServiceInstanceName(manifest.getServiceInstanceName());
    attrs.setSyslogDrainUrl(manifest.getSyslogDrainUrl());
    attrs.setRouteServiceUrl(manifest.getRouteServiceUrl());
    attrs.setTags(manifest.getTags());
    attrs.setCredentialsMap(parseParameters(manifest.getCredentialsMap()));
    return attrs;
  }

  private Map<String, Object> parseParameters(String parameterString) {
    if (!StringUtils.isBlank(parameterString)) {
      try {
        return getObjectMapper()
          .readValue(parameterString, new TypeReference<Map<String, Object>>() {
          });
      } catch (IOException e) {
        throw new IllegalArgumentException("Unable to convert parameters to map: '" + e.getMessage() + "'.");
      }
    }
    return null;
  }

  @Data
  private static class ServiceManifest {
    private String service;

    private String serviceInstanceName;

    private String servicePlan;

    @Nullable
    private Set<String> tags;

    @Nullable
    private String parameters;
  }

  @Data
  private static class UserProvidedServiceManifest {
    private String serviceInstanceName;

    @Nullable
    private String syslogDrainUrl;

    @Nullable
    private String routeServiceUrl;

    @Nullable
    private Set<String> tags;

    @Nullable
    private String credentialsMap;
  }
}
