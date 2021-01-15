/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.converters;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.netflix.spinnaker.clouddriver.artifacts.ArtifactCredentialsRepository;
import com.netflix.spinnaker.clouddriver.cloudfoundry.artifacts.ArtifactCredentialsFromString;
import com.netflix.spinnaker.clouddriver.cloudfoundry.cache.CacheRepository;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.MockCloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.DeployCloudFoundryServiceDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryOrganization;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import com.netflix.spinnaker.clouddriver.cloudfoundry.provider.CloudFoundryProvider;
import com.netflix.spinnaker.clouddriver.cloudfoundry.security.CloudFoundryCredentials;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.credentials.MapBackedCredentialsRepository;
import com.netflix.spinnaker.credentials.NoopCredentialsLifecycleHandler;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;
import javax.annotation.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

class DeployCloudFoundryServiceAtomicOperationConverterTest {

  private final CloudFoundryClient cloudFoundryClient = new MockCloudFoundryClient();
  private final CacheRepository cacheRepository = mock(CacheRepository.class);

  {
    when(cloudFoundryClient.getOrganizations().findByName(any()))
        .thenReturn(
            Optional.of(CloudFoundryOrganization.builder().id("space-guid").name("space").build()));

    when(cloudFoundryClient.getSpaces().findSpaceByRegion(any()))
        .thenAnswer(
            (Answer<Optional<CloudFoundrySpace>>)
                invocation ->
                    Optional.of(
                        CloudFoundrySpace.builder()
                            .id("space-guid")
                            .name("space")
                            .organization(
                                CloudFoundryOrganization.builder()
                                    .id("org-guid")
                                    .name("org")
                                    .build())
                            .build()));
  }

  private final CloudFoundryCredentials cloudFoundryCredentials =
      new CloudFoundryCredentials(
          "test",
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

  private final CredentialsRepository<ArtifactCredentialsFromString>
      artifactCredentialsFromStringCredentialsRepository =
          new MapBackedCredentialsRepository<>(
              ArtifactCredentialsFromString.ARTIFACT_TYPE, new NoopCredentialsLifecycleHandler<>());

  private final ArtifactCredentialsRepository artifactCredentialsRepository =
      new ArtifactCredentialsRepository(
          Collections.singletonList(artifactCredentialsFromStringCredentialsRepository));

  private final CredentialsRepository<CloudFoundryCredentials> credentialsRepository =
      new MapBackedCredentialsRepository<>(CloudFoundryProvider.PROVIDER_ID, null);

  {
    artifactCredentialsFromStringCredentialsRepository.save(
        new ArtifactCredentialsFromString(
            "test",
            List.of("test"),
            "service_instance_name: my-service-instance-name\n"
                + "service: my-service\n"
                + "service_plan: my-service-plan\n"
                + "tags:\n"
                + "- tag1\n"
                + "updatable: false\n"
                + "parameters: |\n"
                + "  { \"foo\": \"bar\" }\n"));
    credentialsRepository.save(cloudFoundryCredentials);
  }

  private final DeployCloudFoundryServiceAtomicOperationConverter converter =
      new DeployCloudFoundryServiceAtomicOperationConverter();

  @BeforeEach
  void initializeClassUnderTest() {
    converter.setCredentialsRepository(credentialsRepository);
  }

  @Test
  void convertManifestMapToServiceAttributes() {
    final Map input =
        Map.of(
            "service", "my-service",
            "service_instance_name", "my-service-instance-name",
            "service_plan", "my-service-plan",
            "tags", List.of("my-tag"),
            "parameters", "{\"foo\": \"bar\"}");

    assertThat(converter.convertManifest(input))
        .isEqualToComparingFieldByFieldRecursively(
            new DeployCloudFoundryServiceDescription.ServiceAttributes()
                .setService("my-service")
                .setServiceInstanceName("my-service-instance-name")
                .setServicePlan("my-service-plan")
                .setTags(Collections.singleton("my-tag"))
                .setUpdatable(true)
                .setParameterMap(Map.<String, Object>of("foo", "bar")));
  }

  @Test
  void convertManifestMapToServiceAttributesMissingServiceThrowsException() {
    final Map input =
        Map.of(
            "service_instance_name", "my-service-instance-name",
            "service_plan", "my-service-plan",
            "tags", Collections.singletonList("my-tag"),
            "parameters", "{\"foo\": \"bar\"}");

    assertThrows(
        IllegalArgumentException.class,
        () -> converter.convertManifest(input),
        "Manifest is missing the service");
  }

  @Test
  void convertManifestMapToServiceAttributesMissingServiceNameThrowsException() {
    final Map input =
        Map.of(
            "service_instance_name", "my-service-instance-name",
            "service_plan", "my-service-plan",
            "tags", Collections.singletonList("my-tag"),
            "parameters", "{\"foo\": \"bar\"}");

    assertThrows(
        IllegalArgumentException.class,
        () -> converter.convertManifest(input),
        "Manifest is missing the service name");
  }

  @Test
  void convertManifestMapToServiceAttributesMissingServicePlanThrowsException() {
    final Map input =
        Map.of(
            "service", "my-service",
            "service_instance_name", "my-service-instance-name",
            "tags", Collections.singletonList("my-tag"),
            "parameters", "{\"foo\": \"bar\"}");

    assertThrows(
        IllegalArgumentException.class,
        () -> converter.convertManifest(input),
        "Manifest is missing the service plan");
  }

  @Test
  void convertCupsManifestMapToUserProvidedServiceAttributes() {
    final Map input =
        Map.of(
            "service_instance_name", "my-service-instance-name",
            "syslog_drain_url", "test-syslog-drain-url",
            "updatable", false,
            "route_service_url", "test-route-service-url",
            "tags", Collections.singletonList("my-tag"),
            "credentials_map", "{\"foo\": \"bar\"}");

    assertThat(converter.convertUserProvidedServiceManifest(input))
        .isEqualToComparingFieldByFieldRecursively(
            new DeployCloudFoundryServiceDescription.UserProvidedServiceAttributes()
                .setServiceInstanceName("my-service-instance-name")
                .setSyslogDrainUrl("test-syslog-drain-url")
                .setRouteServiceUrl("test-route-service-url")
                .setTags(Collections.singleton("my-tag"))
                .setUpdatable(false)
                .setCredentials(Collections.singletonMap("foo", "bar")));
  }

  @Test
  void convertCupsManifestMapToUserProvidedServiceAttributesMissingServiceNameThrowsException() {
    final Map input =
        Map.of(
            "syslog_drain_url", "test-syslog-drain-url",
            "route_service_url", "test-route-service-url",
            "tags", Collections.singletonList("my-tag"),
            "credentials_map", "{\"foo\": \"bar\"}");

    assertThrows(
        IllegalArgumentException.class,
        () -> converter.convertUserProvidedServiceManifest(input),
        "Manifest is missing the service name");
  }

  @Test
  void convertDescriptionWithUserProvidedInput() {
    final Map input =
        Map.of(
            "credentials",
            "test",
            "region",
            "org > space",
            "userProvided",
            true,
            "manifest",
            Collections.singletonList(
                Map.of(
                    "serviceInstanceName", "userProvidedServiceName",
                    "tags", Collections.singletonList("my-tag"),
                    "syslogDrainUrl", "http://syslogDrainUrl.io",
                    "credentials", "{\"foo\": \"bar\"}",
                    "routeServiceUrl", "http://routeServiceUrl.io")));

    final DeployCloudFoundryServiceDescription result = converter.convertDescription(input);
    assertThat(result.getServiceAttributes()).isNull();
    assertThat(result.getUserProvidedServiceAttributes())
        .isEqualToComparingFieldByFieldRecursively(
            new DeployCloudFoundryServiceDescription.UserProvidedServiceAttributes()
                .setServiceInstanceName("userProvidedServiceName")
                .setSyslogDrainUrl("http://syslogDrainUrl.io")
                .setRouteServiceUrl("http://routeServiceUrl.io")
                .setTags(Collections.singleton("my-tag"))
                .setUpdatable(true)
                .setCredentials(Map.of("foo", "bar")));
  }

  private static class WithMap {
    public WithMap() {}

    public WithMap(String key, Object value) {
      this.mapField = Collections.singletonMap(key, value);
    }

    @Nullable
    @JsonDeserialize(
        using =
            DeployCloudFoundryServiceAtomicOperationConverter.OptionallySerializedMapDeserializer
                .class)
    private Map<String, Object> mapField;
  }

  @Test
  void deserializeYamlSerializedMap() {
    final WithMap result =
        new ObjectMapper()
            .convertValue(Collections.singletonMap("mapField", "key1: value1"), WithMap.class);

    assertThat(result).isEqualToComparingFieldByFieldRecursively(new WithMap("key1", "value1"));
  }

  @Test
  void deserializeJsonSerializedMap() {
    final WithMap result =
        new ObjectMapper()
            .convertValue(
                Collections.singletonMap("mapField", "{\"key1\": \"value1\"}}"), WithMap.class);

    assertThat(result).isEqualToComparingFieldByFieldRecursively(new WithMap("key1", "value1"));
  }

  @Test
  void deserializeAlreadyDeserializedMap() {
    final WithMap result =
        new ObjectMapper()
            .convertValue(
                Collections.singletonMap("mapField", Collections.singletonMap("key1", "value1")),
                WithMap.class);

    assertThat(result).isEqualToComparingFieldByFieldRecursively(new WithMap("key1", "value1"));
  }

  @Test
  void deserializeEmptyStringAsMap() {
    final WithMap result =
        new ObjectMapper().convertValue(Collections.singletonMap("mapField", ""), WithMap.class);

    assertThat(result).isEqualToComparingFieldByFieldRecursively(new WithMap());
  }

  @Test
  void deserializeNullStringAsMap() {
    final WithMap result =
        new ObjectMapper().convertValue(Collections.singletonMap("mapField", null), WithMap.class);

    assertThat(result).isEqualToComparingFieldByFieldRecursively(new WithMap());
  }
}
