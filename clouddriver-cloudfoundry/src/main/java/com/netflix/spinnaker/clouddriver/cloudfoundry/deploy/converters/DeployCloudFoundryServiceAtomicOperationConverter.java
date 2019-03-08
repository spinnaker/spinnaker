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

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.artifacts.ArtifactDownloader;
import com.netflix.spinnaker.clouddriver.cloudfoundry.CloudFoundryOperation;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.DeployCloudFoundryServiceDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.ops.DeployCloudFoundryServiceAtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
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
  private static final ObjectMapper objectMapper = new ObjectMapper()
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  private final ArtifactDownloader artifactDownloader;

  public DeployCloudFoundryServiceAtomicOperationConverter(ArtifactDownloader artifactDownloader) {
    this.artifactDownloader = artifactDownloader;
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
    if (manifest.get("artifact") != null) {
      Artifact manifestArtifact = objectMapper.convertValue(manifest.get("artifact"), Artifact.class);
      downloadAndProcessManifest(artifactDownloader, manifestArtifact, myMap -> {
        if (converted.isUserProvided()) {
          converted.setUserProvidedServiceAttributes(convertUserProvidedServiceManifest(myMap));
        } else {
          converted.setServiceAttributes(convertManifest(myMap));
        }
      });
    } else if (manifest.get("direct") != null) {
      if (converted.isUserProvided()) {
        converted.setUserProvidedServiceAttributes(convertUserProvidedServiceManifest(manifest.get("direct")));
      } else {
        converted.setServiceAttributes(convertManifest(manifest.get("direct")));
      }
    }
    return converted;
  }

  // visible for testing
  DeployCloudFoundryServiceDescription.ServiceAttributes convertManifest(Object manifestMap) {
    ServiceManifest manifest = objectMapper.convertValue(manifestMap, new TypeReference<ServiceManifest>() {
    });
    if (manifest.getService() == null) {
      throw new IllegalArgumentException("Manifest is missing the service");
    } else if (manifest.getServiceInstanceName() == null) {
      throw new IllegalArgumentException("Manifest is missing the service instance name");
    } else if (manifest.getServicePlan() == null) {
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

  // visible for testing
  DeployCloudFoundryServiceDescription.UserProvidedServiceAttributes convertUserProvidedServiceManifest(Object manifestMap) {
    UserProvidedServiceManifest manifest = objectMapper.convertValue(manifestMap, new TypeReference<UserProvidedServiceManifest>() {
    });
    if (manifest.getServiceInstanceName() == null) {
      throw new IllegalArgumentException("Manifest is missing the service name");
    }
    DeployCloudFoundryServiceDescription.UserProvidedServiceAttributes attrs = new DeployCloudFoundryServiceDescription.UserProvidedServiceAttributes();
    attrs.setServiceInstanceName(manifest.getServiceInstanceName());
    attrs.setSyslogDrainUrl(manifest.getSyslogDrainUrl());
    attrs.setRouteServiceUrl(manifest.getRouteServiceUrl());
    attrs.setTags(manifest.getTags());
    attrs.setCredentials(parseParameters(manifest.getCredentials()));
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

    @JsonAlias("service_instance_name")
    private String serviceInstanceName;

    @JsonAlias("service_plan")
    private String servicePlan;

    @Nullable
    private Set<String> tags;

    @Nullable
    private String parameters;
  }

  @Data
  private static class UserProvidedServiceManifest {
    @JsonAlias("service_instance_name")
    private String serviceInstanceName;

    @Nullable
    @JsonAlias("syslog_drain_url")
    private String syslogDrainUrl;

    @Nullable
    @JsonAlias("route_service_url")
    private String routeServiceUrl;

    @Nullable
    private Set<String> tags;

    @Nullable
    @JsonAlias("credentials_map")
    private String credentials;
  }
}
