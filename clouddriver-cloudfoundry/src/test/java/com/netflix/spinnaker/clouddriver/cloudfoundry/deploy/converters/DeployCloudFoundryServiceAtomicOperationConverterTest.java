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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.artifacts.ArtifactCredentialsRepository;
import com.netflix.spinnaker.clouddriver.artifacts.ArtifactDownloader;
import com.netflix.spinnaker.clouddriver.cloudfoundry.artifacts.ArtifactCredentialsFromString;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.MockCloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.DeployCloudFoundryServiceDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryOrganization;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import com.netflix.spinnaker.clouddriver.cloudfoundry.security.CloudFoundryCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

class DeployCloudFoundryServiceAtomicOperationConverterTest {

  private final CloudFoundryClient cloudFoundryClient = new MockCloudFoundryClient();

  {
    when(cloudFoundryClient.getOrganizations().findByName(any()))
        .thenReturn(
            Optional.of(CloudFoundryOrganization.builder().id("space-guid").name("space").build()));

    when(cloudFoundryClient.getOrganizations().findSpaceByRegion(any()))
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
      new CloudFoundryCredentials("test", "", "", "", "", "", "") {
        public CloudFoundryClient getClient() {
          return cloudFoundryClient;
        }
      };

  private final ArtifactCredentialsRepository artifactCredentialsRepository =
      new ArtifactCredentialsRepository(
          Collections.singletonList(
              Collections.singletonList(
                  new ArtifactCredentialsFromString(
                      "test",
                      List.of("test").asJava(),
                      "service_instance_name: my-service-instance-name\n"
                          + "service: my-service\n"
                          + "service_plan: my-service-plan\n"
                          + "tags:\n"
                          + "- tag1\n"
                          + "updatable: false\n"
                          + "parameters: |\n"
                          + "  { \"foo\": \"bar\" }\n"))));

  private final AccountCredentialsRepository accountCredentialsRepository =
      new MapBackedAccountCredentialsRepository();

  {
    accountCredentialsRepository.update("test", cloudFoundryCredentials);
  }

  private final AccountCredentialsProvider accountCredentialsProvider =
      new DefaultAccountCredentialsProvider(accountCredentialsRepository);
  private final DeployCloudFoundryServiceAtomicOperationConverter converter =
      new DeployCloudFoundryServiceAtomicOperationConverter(
          new ArtifactDownloader(artifactCredentialsRepository));

  @BeforeEach
  void initializeClassUnderTest() {
    converter.setAccountCredentialsProvider(accountCredentialsProvider);
    converter.setObjectMapper(new ObjectMapper());
  }

  @Test
  void convertManifestMapToServiceAttributes() {
    final Map input =
        HashMap.of(
                "service", "my-service",
                "service_instance_name", "my-service-instance-name",
                "service_plan", "my-service-plan",
                "tags", List.of("my-tag").asJava(),
                "parameters", "{\"foo\": \"bar\"}")
            .toJavaMap();

    assertThat(converter.convertManifest(input))
        .isEqualToComparingFieldByFieldRecursively(
            new DeployCloudFoundryServiceDescription.ServiceAttributes()
                .setService("my-service")
                .setServiceInstanceName("my-service-instance-name")
                .setServicePlan("my-service-plan")
                .setTags(Collections.singleton("my-tag"))
                .setUpdatable(true)
                .setParameterMap(HashMap.<String, Object>of("foo", "bar").toJavaMap()));
  }

  @Test
  void convertManifestMapToServiceAttributesMissingServiceThrowsException() {
    final Map input =
        HashMap.of(
                "service_instance_name", "my-service-instance-name",
                "service_plan", "my-service-plan",
                "tags", List.of("my-tag").asJava(),
                "parameters", "{\"foo\": \"bar\"}")
            .toJavaMap();

    assertThrows(
        IllegalArgumentException.class,
        () -> converter.convertManifest(input),
        "Manifest is missing the service");
  }

  @Test
  void convertManifestMapToServiceAttributesMissingServiceNameThrowsException() {
    final Map input =
        HashMap.of(
                "service_instance_name", "my-service-instance-name",
                "service_plan", "my-service-plan",
                "tags", List.of("my-tag").asJava(),
                "parameters", "{\"foo\": \"bar\"}")
            .toJavaMap();

    assertThrows(
        IllegalArgumentException.class,
        () -> converter.convertManifest(input),
        "Manifest is missing the service name");
  }

  @Test
  void convertManifestMapToServiceAttributesMissingServicePlanThrowsException() {
    final Map input =
        HashMap.of(
                "service", "my-service",
                "service_instance_name", "my-service-instance-name",
                "tags", List.of("my-tag").asJava(),
                "parameters", "{\"foo\": \"bar\"}")
            .toJavaMap();

    assertThrows(
        IllegalArgumentException.class,
        () -> converter.convertManifest(input),
        "Manifest is missing the service plan");
  }

  @Test
  void convertCupsManifestMapToUserProvidedServiceAttributes() {
    final Map input =
        HashMap.of(
                "service_instance_name", "my-service-instance-name",
                "syslog_drain_url", "test-syslog-drain-url",
                "updatable", false,
                "route_service_url", "test-route-service-url",
                "tags", List.of("my-tag").asJava(),
                "credentials_map", "{\"foo\": \"bar\"}")
            .toJavaMap();

    assertThat(converter.convertUserProvidedServiceManifest(input))
        .isEqualToComparingFieldByFieldRecursively(
            new DeployCloudFoundryServiceDescription.UserProvidedServiceAttributes()
                .setServiceInstanceName("my-service-instance-name")
                .setSyslogDrainUrl("test-syslog-drain-url")
                .setRouteServiceUrl("test-route-service-url")
                .setTags(Collections.singleton("my-tag"))
                .setUpdatable(false)
                .setCredentials(HashMap.<String, Object>of("foo", "bar").toJavaMap()));
  }

  @Test
  void convertCupsManifestMapToUserProvidedServiceAttributesMissingServiceNameThrowsException() {
    final Map input =
        HashMap.of(
                "syslog_drain_url", "test-syslog-drain-url",
                "route_service_url", "test-route-service-url",
                "tags", List.of("my-tag").asJava(),
                "credentials_map", "{\"foo\": \"bar\"}")
            .toJavaMap();

    assertThrows(
        IllegalArgumentException.class,
        () -> converter.convertUserProvidedServiceManifest(input),
        "Manifest is missing the service name");
  }

  @Test
  void convertManifestMapToServiceAttributesEmptyParamString() {
    final Map input =
        HashMap.of(
                "service", "my-service",
                "service_instance_name", "my-service-instance-name",
                "service_plan", "my-service-plan",
                "tags", List.of("my-tag").asJava(),
                "updatable", true,
                "parameters", "")
            .toJavaMap();

    assertThat(converter.convertManifest(input))
        .isEqualToComparingFieldByFieldRecursively(
            new DeployCloudFoundryServiceDescription.ServiceAttributes()
                .setService("my-service")
                .setServiceInstanceName("my-service-instance-name")
                .setServicePlan("my-service-plan")
                .setTags(Collections.singleton("my-tag"))
                .setUpdatable(true));
  }

  @Test
  void convertManifestMapToServiceAttributesBadParamString() {
    final Map input = HashMap.of("parameters", "[\"foo\", \"bar\"]").toJavaMap();

    assertThrows(
        IllegalArgumentException.class,
        () -> converter.convertManifest(input),
        "Unable to convert parameters to map: 'Unexpected character (',' (code 44)): was expecting a colon to separate field name and value");
  }

  @Test
  void convertDescriptionWithDownloadedManifest() {
    final Map input =
        HashMap.of(
                "credentials", "test",
                "region", "org > space",
                "manifest",
                    HashMap.of(
                            "artifact",
                            HashMap.of(
                                    "artifactAccount", "test",
                                    "reference", "ref1",
                                    "type", "test")
                                .toJavaMap())
                        .toJavaMap())
            .toJavaMap();

    final DeployCloudFoundryServiceDescription result = converter.convertDescription(input);

    assertThat(result.getSpace())
        .isEqualToComparingFieldByFieldRecursively(
            CloudFoundrySpace.builder()
                .id("space-guid")
                .name("space")
                .organization(CloudFoundryOrganization.builder().id("org-guid").name("org").build())
                .build());
    assertThat(result.getServiceAttributes())
        .isEqualToComparingFieldByFieldRecursively(
            new DeployCloudFoundryServiceDescription.ServiceAttributes()
                .setService("my-service")
                .setServiceInstanceName("my-service-instance-name")
                .setServicePlan("my-service-plan")
                .setTags(Collections.singleton("tag1"))
                .setUpdatable(false)
                .setParameterMap(HashMap.<String, Object>of("foo", "bar").toJavaMap()));
  }

  @Test
  void convertDescriptionWithUserProvidedInput() {
    final Map input =
        HashMap.of(
                "credentials",
                "test",
                "region",
                "org > space",
                "userProvided",
                true,
                "manifest",
                HashMap.of(
                        "direct",
                        HashMap.of(
                                "serviceInstanceName", "userProvidedServiceName",
                                "tags", List.of("my-tag").asJava(),
                                "syslogDrainUrl", "http://syslogDrainUrl.io",
                                "credentials", "{\"foo\": \"bar\"}",
                                "routeServiceUrl", "http://routeServiceUrl.io")
                            .toJavaMap())
                    .toJavaMap())
            .toJavaMap();

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
                .setCredentials(HashMap.<String, Object>of("foo", "bar").toJavaMap()));
  }

  @Test
  void convertDescriptionWithUserProvidedInputWithoutCredentials() {
    final Map input =
        HashMap.of(
                "credentials",
                "test",
                "region",
                "org > space",
                "userProvided",
                true,
                "manifest",
                HashMap.of(
                        "direct",
                        HashMap.of(
                                "serviceInstanceName", "userProvidedServiceName",
                                "tags", List.of("my-tag").asJava(),
                                "updatable", false,
                                "syslogDrainUrl", "http://syslogDrainUrl.io",
                                "routeServiceUrl", "http://routeServiceUrl.io")
                            .toJavaMap())
                    .toJavaMap())
            .toJavaMap();

    final DeployCloudFoundryServiceDescription result = converter.convertDescription(input);
    assertThat(result.getServiceAttributes()).isNull();
    assertThat(result.getUserProvidedServiceAttributes())
        .isEqualToComparingFieldByFieldRecursively(
            new DeployCloudFoundryServiceDescription.UserProvidedServiceAttributes()
                .setServiceInstanceName("userProvidedServiceName")
                .setSyslogDrainUrl("http://syslogDrainUrl.io")
                .setRouteServiceUrl("http://routeServiceUrl.io")
                .setTags(Collections.singleton("my-tag"))
                .setUpdatable(false));
  }
}
