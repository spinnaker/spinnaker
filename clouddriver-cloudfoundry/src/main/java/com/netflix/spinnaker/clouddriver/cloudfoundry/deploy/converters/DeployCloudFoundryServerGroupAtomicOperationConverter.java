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
import com.google.common.annotations.VisibleForTesting;
import com.netflix.spinnaker.clouddriver.artifacts.ArtifactCredentialsRepository;
import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials;
import com.netflix.spinnaker.clouddriver.artifacts.gcs.GcsArtifactCredentials;
import com.netflix.spinnaker.clouddriver.artifacts.http.HttpArtifactCredentials;
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
import io.vavr.collection.Stream;
import lombok.Data;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.vavr.API.*;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

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
    CloudFoundryCredentials credentials = getCredentialsObject(input.get("credentials").toString());
    converted.setClient(credentials.getClient());
    converted.setAccountName(credentials.getName());

    converted.setSpace(findSpace(converted.getRegion(), converted.getClient())
      .orElseThrow(() -> new IllegalArgumentException("Unable to find space '" + converted.getRegion() + "'.")));

    Map artifactSource = (Map) input.get("artifactSource");

    if ("artifact".equals(artifactSource.get("type"))) {
      ArtifactCredentials artifactCredentials = credentialsRepository.getAllCredentials().stream()
        .filter(creds -> creds.getName().equals(artifactSource.get("account")))
        .findAny()
        .orElseThrow(() -> new IllegalArgumentException("Unable to find artifact credentials '" + artifactSource.get("account") + "'"));

      converted.setArtifact(convertToArtifact(artifactSource.get("account").toString(), artifactSource.get("reference").toString()));
      converted.setArtifactCredentials(artifactCredentials);
    } else if ("package".equals(artifactSource.get("type"))) {
      CloudFoundryCredentials accountCredentials = getCredentialsObject(artifactSource.get("account").toString());
      converted.setArtifactCredentials(new PackageArtifactCredentials(credentials.getClient()));

      Artifact artifact = new Artifact();
      artifact.setType("package");
      artifact.setReference(getServerGroupId(artifactSource.get("serverGroupName").toString(),
        artifactSource.get("region").toString(), accountCredentials.getClient()));
      converted.setArtifact(artifact);
    }

    Map manifest = (Map) input.get("manifest");
    if ("direct".equals(manifest.get("type"))) {
      DeployCloudFoundryServerGroupDescription.ApplicationAttributes attrs = getObjectMapper().convertValue(manifest, DeployCloudFoundryServerGroupDescription.ApplicationAttributes.class);
      converted.setApplicationAttributes(attrs);
    } else if ("artifact".equals(manifest.get("type"))) {
      Artifact manifestArtifact = convertToArtifact(manifest.get("account").toString(), manifest.get("reference").toString());
      ArtifactCredentials manifestArtifactCredentials = credentialsRepository.getAllCredentials().stream()
        .filter(creds -> creds.getName().equals(manifest.get("account")))
        .findAny()
        .orElseThrow(() -> new IllegalArgumentException("Unable to find manifest credentials '" + manifest.get("account") + "'"));

      try {
        InputStream manifestInput = manifestArtifactCredentials.download(manifestArtifact);
        Yaml parser = new Yaml();
        Map manifestMap = (Map) parser.load(manifestInput);
        final Optional<DeployCloudFoundryServerGroupDescription.ApplicationAttributes> attrs = convertManifest(manifestMap);
        attrs.ifPresent(a -> {
          converted.setApplicationAttributes(a);
        });
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    return converted;
  }

  @VisibleForTesting
  Optional<DeployCloudFoundryServerGroupDescription.ApplicationAttributes> convertManifest(Map manifestMap) {
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
      attrs.setEnv(app.getEnv() == null ? null : app.getEnv().stream().flatMap(env -> env.entrySet().stream()).collect(toMap(Map.Entry::getKey, Map.Entry::getValue)));
      return attrs;
    });
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
    private List<Map<String, String>> env;
  }

  private Artifact convertToArtifact(String account, String reference) {
    ArtifactCredentials artifactCredentials = credentialsRepository.getAllCredentials().stream()
      .filter(creds -> account.equals(creds.getName()))
      .findAny()
      .orElse(null);

    Artifact artifact = new Artifact();
    artifact.setReference(reference);

    if (artifactCredentials == null) {
      artifact.setType("http/file");
    } else if (artifactCredentials instanceof HttpArtifactCredentials) {
      artifact.setType("http/file");
    } else if (artifactCredentials instanceof GcsArtifactCredentials) {
      artifact.setType("gcs/file");
    }

    return artifact;
  }
}
