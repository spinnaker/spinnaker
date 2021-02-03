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

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.clouddriver.artifacts.ArtifactCredentialsRepository;
import com.netflix.spinnaker.clouddriver.cloudfoundry.artifacts.ArtifactCredentialsFromString;
import com.netflix.spinnaker.clouddriver.cloudfoundry.cache.CacheRepository;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.MockCloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.DeployCloudFoundryServerGroupDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryOrganization;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import com.netflix.spinnaker.clouddriver.cloudfoundry.provider.CloudFoundryProvider;
import com.netflix.spinnaker.clouddriver.cloudfoundry.security.CloudFoundryCredentials;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.credentials.MapBackedCredentialsRepository;
import com.netflix.spinnaker.credentials.NoopCredentialsLifecycleHandler;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

class DeployCloudFoundryServerGroupAtomicOperationConverterTest {

  private static CloudFoundryCredentials createCredentials(String name) {
    CloudFoundryClient cloudFoundryClient = new MockCloudFoundryClient();
    CacheRepository cacheRepository = mock(CacheRepository.class);
    {
      when(cloudFoundryClient.getOrganizations().findByName(any()))
          .thenAnswer(
              (Answer<Optional<CloudFoundryOrganization>>)
                  invocation -> {
                    Object[] args = invocation.getArguments();
                    return Optional.of(
                        CloudFoundryOrganization.builder()
                            .id(args[0].toString() + "-guid")
                            .name(args[0].toString())
                            .build());
                  });

      when(cloudFoundryClient.getSpaces().findSpaceByRegion(any()))
          .thenReturn(
              Optional.of(
                  CloudFoundrySpace.builder()
                      .id("space-guid")
                      .name("space")
                      .organization(
                          CloudFoundryOrganization.builder().id("org-guid").name("org").build())
                      .build()));

      when(cloudFoundryClient.getApplications().findServerGroupId(any(), any()))
          .thenReturn("servergroup-id");
    }

    return new CloudFoundryCredentials(
        name,
        "",
        "",
        "",
        "",
        "",
        "",
        false,
        500,
        cacheRepository,
        null,
        ForkJoinPool.commonPool(),
        emptyMap()) {
      public CloudFoundryClient getClient() {
        return cloudFoundryClient;
      }
    };
  }

  private List<String> accounts =
      List.of("test", "sourceAccount", "sourceAccount1", "sourceAccount2", "destinationAccount");

  private CredentialsRepository<ArtifactCredentialsFromString>
      artifactCredentialsFromStringRepository =
          new MapBackedCredentialsRepository<>(
              ArtifactCredentialsFromString.ARTIFACT_TYPE, new NoopCredentialsLifecycleHandler<>());
  private final ArtifactCredentialsRepository artifactCredentialsRepository =
      new ArtifactCredentialsRepository(
          Collections.singletonList(artifactCredentialsFromStringRepository));

  private final CredentialsRepository<CloudFoundryCredentials> credentialsRepository =
      new MapBackedCredentialsRepository<>(CloudFoundryProvider.PROVIDER_ID, null);

  {
    accounts.stream()
        .map(
            account ->
                new ArtifactCredentialsFromString(
                    account, List.of("test"), "applications: [{instances: 42}]"))
        .forEach(artifactCredentialsFromStringRepository::save);
    accounts.forEach(account -> credentialsRepository.save(createCredentials(account)));
  }

  private final DeployCloudFoundryServerGroupAtomicOperationConverter converter =
      new DeployCloudFoundryServerGroupAtomicOperationConverter(
          null, artifactCredentialsRepository, emptyList());

  @BeforeEach
  void initializeClassUnderTest() {
    converter.setCredentialsRepository(credentialsRepository);
  }

  @Test
  void convertManifestMapToApplicationAttributes() {
    final Map input =
        Map.of(
            "applications",
            List.of(
                Map.of(
                    "instances",
                    7,
                    "memory",
                    "1G",
                    "disk_quota",
                    "2048M",
                    "health-check-type",
                    "http",
                    "health-check-http-endpoint",
                    "/health",
                    "buildpacks",
                    List.of("buildpack1", "buildpack2"),
                    "services",
                    List.of("service1"),
                    "routes",
                    List.of(Map.of("route", "www.example.com/foo")),
                    "env",
                    Map.of("token", "ASDF"),
                    "command",
                    "some-command")));

    assertThat(converter.convertManifest(input))
        .isEqualToComparingFieldByFieldRecursively(
            new DeployCloudFoundryServerGroupDescription.ApplicationAttributes()
                .setInstances(7)
                .setMemory("1G")
                .setDiskQuota("2048M")
                .setHealthCheckType("http")
                .setHealthCheckHttpEndpoint("/health")
                .setBuildpacks(List.of("buildpack1", "buildpack2"))
                .setServices(List.of("service1"))
                .setRoutes(List.of("www.example.com/foo"))
                .setEnv(Map.of("token", "ASDF"))
                .setCommand("some-command"));
  }

  @Test
  void convertManifestMapToApplicationAttributesUsingDeprecatedBuildpackAttr() {
    final Map input = Map.of("applications", List.of(Map.of("buildpack", "buildpack1")));

    assertThat(converter.convertManifest(input))
        .isEqualToComparingFieldByFieldRecursively(
            new DeployCloudFoundryServerGroupDescription.ApplicationAttributes()
                .setInstances(1)
                .setMemory("1024")
                .setDiskQuota("1024")
                .setBuildpacks(List.of("buildpack1")));
  }

  @Test
  void convertManifestMapToApplicationAttributesUsingDeprecatedBuildpackAttrBlankStringValue() {
    final Map input = Map.of("applications", List.of(Map.of("buildpack", "")));

    assertThat(converter.convertManifest(input))
        .isEqualToComparingFieldByFieldRecursively(
            new DeployCloudFoundryServerGroupDescription.ApplicationAttributes()
                .setInstances(1)
                .setMemory("1024")
                .setDiskQuota("1024")
                .setBuildpacks(Collections.emptyList()));
  }

  @Test
  void convertManifestMapToApplicationAttributesUsingWithNoBuildpacks() {
    final Map input = Map.of("applications", List.of(Collections.EMPTY_MAP));

    assertThat(converter.convertManifest(input))
        .isEqualToComparingFieldByFieldRecursively(
            new DeployCloudFoundryServerGroupDescription.ApplicationAttributes()
                .setInstances(1)
                .setMemory("1024")
                .setDiskQuota("1024")
                .setBuildpacks(Collections.emptyList()));
  }

  @Test
  void convertDescriptionTest() {
    Map<String, Object> description =
        ImmutableMap.of(
            "applicationArtifact",
                ImmutableMap.of(
                    "artifactAccount",
                    "destinationAccount",
                    "type",
                    "cloudfoundry/app",
                    "name",
                    "server-group-name",
                    "location",
                    "cf-region"),
            "credentials", "test",
            "manifest",
                ImmutableList.of(
                    ImmutableMap.of("applications", ImmutableList.of(ImmutableMap.of()))));

    DeployCloudFoundryServerGroupDescription result = converter.convertDescription(description);

    assertThat(result.getArtifactCredentials()).isNotNull();
    assertThat(result.getArtifactCredentials().getName()).isEqualTo("cloudfoundry");
    assertThat(result.getApplicationArtifact()).isNotNull();
    assertThat(result.getApplicationArtifact().getName()).isEqualTo("server-group-name");
    assertThat(result.getApplicationArtifact().getArtifactAccount())
        .isEqualTo("destinationAccount");
    assertThat(result.getApplicationArtifact().getUuid()).isEqualTo("servergroup-id");
  }
}
