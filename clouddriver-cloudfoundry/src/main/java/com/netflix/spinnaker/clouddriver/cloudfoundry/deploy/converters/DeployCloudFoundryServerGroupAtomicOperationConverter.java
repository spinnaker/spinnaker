/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.converters;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.netflix.spinnaker.clouddriver.artifacts.ArtifactCredentialsRepository;
import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials;
import com.netflix.spinnaker.clouddriver.cloudfoundry.CloudFoundryOperation;
import com.netflix.spinnaker.clouddriver.cloudfoundry.artifacts.PackageArtifactCredentials;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.DeployCloudFoundryServerGroupDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.ops.DeployCloudFoundryServerGroupAtomicOperation;
import com.netflix.spinnaker.clouddriver.cloudfoundry.provider.view.CloudFoundryClusterProvider;
import com.netflix.spinnaker.clouddriver.cloudfoundry.security.CloudFoundryCredentials;
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import lombok.Data;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.vavr.API.*;
import static java.util.stream.Collectors.toList;

@CloudFoundryOperation(AtomicOperations.CREATE_SERVER_GROUP)
@Component
public class DeployCloudFoundryServerGroupAtomicOperationConverter extends AbstractCloudFoundryServerGroupAtomicOperationConverter {
  private final OperationPoller operationPoller;
  private final ArtifactCredentialsRepository credentialsRepository;
  private final CloudFoundryClusterProvider clusterProvider;

  public DeployCloudFoundryServerGroupAtomicOperationConverter(@Qualifier("cloudFoundryOperationPoller") OperationPoller operationPoller,
                                                               ArtifactCredentialsRepository credentialsRepository,
                                                               CloudFoundryClusterProvider clusterProvider) {
    this.operationPoller = operationPoller;
    this.credentialsRepository = credentialsRepository;
    this.clusterProvider = clusterProvider;
  }

  @Override
  public AtomicOperation convertOperation(Map input) {
    return new DeployCloudFoundryServerGroupAtomicOperation(operationPoller, convertDescription(input), clusterProvider);
  }

  @Override
  public DeployCloudFoundryServerGroupDescription convertDescription(Map input) {
    DeployCloudFoundryServerGroupDescription converted = getObjectMapper().convertValue(input, DeployCloudFoundryServerGroupDescription.class);
    String deployCredentials = Optional.ofNullable(converted.getDestination())
      .map(DeployCloudFoundryServerGroupDescription.Destination::getAccount)
      .orElse(input.get("credentials").toString());
    CloudFoundryCredentials credentials = getCredentialsObject(deployCredentials);
    converted.setClient(credentials.getClient());
    converted.setAccountName(credentials.getName());

    String region = Optional.ofNullable(converted.getDestination())
      .map(DeployCloudFoundryServerGroupDescription.Destination::getRegion)
      .orElse(converted.getRegion());
    converted.setSpace(findSpace(region, converted.getClient())
      .orElseThrow(() -> new IllegalArgumentException("Unable to find space '" + region + "'.")));

    Map artifactSource = (Map) input.get("artifact");

    if (Optional.ofNullable(converted.getSource()).isPresent()) {
      CloudFoundryCredentials artifactCredentials = getCredentialsObject(converted.getSource().getAccount());
      converted.setArtifactCredentials(new PackageArtifactCredentials(artifactCredentials.getClient()));

      Artifact artifact = new Artifact();
      artifact.setType("package");
      artifact.setReference(getServerGroupId(converted.getSource().getAsgName(),
        converted.getSource().getRegion(), artifactCredentials.getClient()));
      converted.setArtifact(artifact);
    } else {
      ArtifactCredentials artifactCredentials = credentialsRepository.getAllCredentials().stream()
        .filter(creds -> creds.getName().equals(artifactSource.get("account")))
        .findAny()
        .orElseThrow(() -> new IllegalArgumentException("Unable to find artifact credentials '" + artifactSource.get("account") + "'"));

      converted.setArtifact(convertToArtifact(artifactCredentials, artifactSource.get("reference").toString()));
      converted.setArtifactCredentials(artifactCredentials);
    }

    Map manifest = (Map) input.get("manifest");
    if ("direct".equals(manifest.get("type"))) {
      DeployCloudFoundryServerGroupDescription.ApplicationAttributes attrs = getObjectMapper().convertValue(manifest, DeployCloudFoundryServerGroupDescription.ApplicationAttributes.class);
      converted.setApplicationAttributes(attrs);
    } else if ("artifact".equals(manifest.get("type"))) {
      downloadAndProcessManifest(manifest, credentialsRepository, myMap -> converted.setApplicationAttributes(convertManifest(myMap)));
    }
    return converted;
  }

  // visible for testing
  DeployCloudFoundryServerGroupDescription.ApplicationAttributes convertManifest(Map manifestMap) {
    List<CloudFoundryManifest> manifestApps = new ObjectMapper()
      .setPropertyNamingStrategy(PropertyNamingStrategy.KEBAB_CASE)
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .convertValue(manifestMap.get("applications"), new TypeReference<List<CloudFoundryManifest>>() {
      });

    return manifestApps.stream().findFirst().map(app -> {
      final List<String> buildpacks = Match(app).of(
        Case($(a -> a.getBuildpacks() != null), app.getBuildpacks()),
        Case($(a -> a.getBuildpack() != null && a.getBuildpack().length() > 0),
          Collections.singletonList(app.getBuildpack())),
        Case($(), Collections.emptyList())
      );

      DeployCloudFoundryServerGroupDescription.ApplicationAttributes attrs = new DeployCloudFoundryServerGroupDescription.ApplicationAttributes();
      attrs.setInstances(app.getInstances() == null ? 1 : app.getInstances());
      attrs.setMemory(app.getMemory() == null ? "1024" : app.getMemory());
      attrs.setDiskQuota(app.getDiskQuota() == null ? "1024" : app.getDiskQuota());
      attrs.setHealthCheckHttpEndpoint(app.getHealthCheckHttpEndpoint());
      attrs.setHealthCheckType(app.getHealthCheckType());
      attrs.setBuildpacks(buildpacks);
      attrs.setServices(app.getServices());
      attrs.setRoutes(app.getRoutes() == null ? null : app.getRoutes().stream().flatMap(route -> route.values().stream()).collect(toList()));
      attrs.setEnv(app.getEnv());
      return attrs;
    }).get();
  }

  @Data
  private static class CloudFoundryManifest {
    @Nullable
    private Integer instances;

    @Nullable
    private String memory;

    @Nullable
    @JsonProperty("disk_quota")
    private String diskQuota;

    @Nullable
    private String healthCheckType;

    @Nullable
    private String healthCheckHttpEndpoint;

    @Nullable
    private String buildpack;

    @Nullable
    private List<String> buildpacks;

    @Nullable
    private List<String> services;

    @Nullable
    private List<Map<String, String>> routes;

    @Nullable
    private Map<String, String> env;
  }

}
