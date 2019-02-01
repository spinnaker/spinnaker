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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.artifacts.ArtifactCredentialsRepository;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

class DeployCloudFoundryServiceAtomicOperationConverterTest {

  private final CloudFoundryClient cloudFoundryClient = new MockCloudFoundryClient();

  {
    when(cloudFoundryClient.getOrganizations().findByName(any()))
      .thenAnswer((Answer<Optional<CloudFoundryOrganization>>) invocation -> {
        Object[] args = invocation.getArguments();
        return Optional.of(CloudFoundryOrganization.builder()
          .id(args[0].toString() + "ID").name(args[0].toString()).build());
      });

    when(cloudFoundryClient.getSpaces().findByName(any(), any())).thenAnswer((Answer<CloudFoundrySpace>) invocation -> {
      Object[] args = invocation.getArguments();
      return CloudFoundrySpace.builder().id(args[1].toString() + "ID").name(args[1].toString())
        .organization(CloudFoundryOrganization.builder()
          .id(args[0].toString()).name(args[0].toString().replace("ID", "")).build()).build();
    });
  }

  private final CloudFoundryCredentials cloudFoundryCredentials = new CloudFoundryCredentials(
    "test", "", "", "", "", "", "") {
    public CloudFoundryClient getClient() {
      return cloudFoundryClient;
    }
  };

  private final ArtifactCredentialsRepository artifactCredentialsRepository = new ArtifactCredentialsRepository();

  {
    artifactCredentialsRepository.save(new ArtifactCredentialsFromString(
      "test",
      List.of("a").asJava(),
      "service_name: my-service-name\n" +
        "service: my-service\n" +
        "service_plan: my-service-plan\n" +
        "tags:\n" +
        "- tag1\n" +
        "parameters: |\n" +
        "  { \"foo\": \"bar\" }\n"
    ));
  }

  private final AccountCredentialsRepository accountCredentialsRepository = new MapBackedAccountCredentialsRepository();

  {
    accountCredentialsRepository.update("test", cloudFoundryCredentials);
  }

  private final AccountCredentialsProvider accountCredentialsProvider =
    new DefaultAccountCredentialsProvider(accountCredentialsRepository);
  private final DeployCloudFoundryServiceAtomicOperationConverter converter =
    new DeployCloudFoundryServiceAtomicOperationConverter(artifactCredentialsRepository);

  @BeforeEach
  void initializeClassUnderTest() {
    converter.setAccountCredentialsProvider(accountCredentialsProvider);
    converter.setObjectMapper(new ObjectMapper());
  }

  @Test
  void convertManifestMapToServiceAttributes() {
    final Map input = HashMap.of(
      "service", "my-service",
      "service_name", "my-service-name",
      "service_plan", "my-service-plan",
      "tags", List.of(
        "my-tag"
      ).asJava(),
      "parameters", "{\"foo\": \"bar\"}"
    ).toJavaMap();

    assertThat(converter.convertManifest(input)).isEqualToComparingFieldByFieldRecursively(
      new DeployCloudFoundryServiceDescription.ServiceAttributes()
        .setService("my-service")
        .setServiceName("my-service-name")
        .setServicePlan("my-service-plan")
        .setTags(Collections.singleton("my-tag"))
        .setParameterMap(HashMap.<String, Object>of(
          "foo", "bar"
        ).toJavaMap())
    );
  }

  @Test
  void convertManifestMapToServiceAttributesMissingServiceThrowsException() {
    final Map input = HashMap.of(
      "service_name", "my-service-name",
      "service_plan", "my-service-plan",
      "tags", List.of(
        "my-tag"
      ).asJava(),
      "parameters", "{\"foo\": \"bar\"}"
    ).toJavaMap();

    assertThrows(IllegalArgumentException.class, () -> converter.convertManifest(input), "Manifest is missing the service");
  }

  @Test
  void convertManifestMapToServiceAttributesMissingServiceNameThrowsException() {
    final Map input = HashMap.of(
      "service_name", "my-service-name",
      "service_plan", "my-service-plan",
      "tags", List.of(
        "my-tag"
      ).asJava(),
      "parameters", "{\"foo\": \"bar\"}"
    ).toJavaMap();

    assertThrows(IllegalArgumentException.class, () -> converter.convertManifest(input), "Manifest is missing the service name");
  }

  @Test
  void convertManifestMapToServiceAttributesMissingServicePlanThrowsException() {
    final Map input = HashMap.of(
      "service", "my-service",
      "service_name", "my-service-name",
      "tags", List.of(
        "my-tag"
      ).asJava(),
      "parameters", "{\"foo\": \"bar\"}"
    ).toJavaMap();

    assertThrows(IllegalArgumentException.class, () -> converter.convertManifest(input), "Manifest is missing the service plan");
  }

  @Test
  void convertCupsManifestMapToUserProvidedServiceAttributes() {
    final Map input = HashMap.of(
      "service_name", "my-service-name",
      "syslog_drain_url", "test-syslog-drain-url",
      "route_service_url", "test-route-service-url",
      "tags", List.of(
        "my-tag"
      ).asJava(),
      "credentials_map", "{\"foo\": \"bar\"}"
    ).toJavaMap();

    assertThat(converter.convertUserProvidedServiceManifest(input)).isEqualToComparingFieldByFieldRecursively(
      new DeployCloudFoundryServiceDescription.UserProvidedServiceAttributes()
        .setServiceName("my-service-name")
        .setSyslogDrainUrl("test-syslog-drain-url")
        .setRouteServiceUrl("test-route-service-url")
        .setTags(Collections.singleton("my-tag"))
        .setCredentialsMap(HashMap.<String, Object>of(
          "foo", "bar"
        ).toJavaMap())
    );
  }

  @Test
  void convertCupsManifestMapToUserProvidedServiceAttributesMissingServiceNameThrowsException() {
    final Map input = HashMap.of(
      "syslog_drain_url", "test-syslog-drain-url",
      "route_service_url", "test-route-service-url",
      "tags", List.of(
        "my-tag"
      ).asJava(),
      "credentials_map", "{\"foo\": \"bar\"}"
    ).toJavaMap();

    assertThrows(IllegalArgumentException.class, () -> converter.convertUserProvidedServiceManifest(input), "Manifest is missing the service name");
  }

  @Test
  void convertManifestMapToServiceAttributesEmptyParamString() {
    final Map input = HashMap.of(
      "service", "my-service",
      "service_name", "my-service-name",
      "service_plan", "my-service-plan",
      "tags", List.of(
        "my-tag"
      ).asJava(),
      "parameters", ""
    ).toJavaMap();

    assertThat(converter.convertManifest(input)).isEqualToComparingFieldByFieldRecursively(
      new DeployCloudFoundryServiceDescription.ServiceAttributes()
        .setService("my-service")
        .setServiceName("my-service-name")
        .setServicePlan("my-service-plan")
        .setTags(Collections.singleton("my-tag"))
    );
  }

  @Test
  void convertManifestMapToServiceAttributesBadParamString() {
    final Map input = HashMap.of(
      "parameters", "[\"foo\", \"bar\"]"
    ).toJavaMap();

    assertThrows(IllegalArgumentException.class,
      () -> converter.convertManifest(input),
      "Unable to convert parameters to map: 'Unexpected character (',' (code 44)): was expecting a colon to separate field name and value");
  }

  @Test
  void convertDescriptionWithDownloadedManifest() {
    final Map input = HashMap.of(
      "credentials", "test",
      "region", "org > space",
      "manifest", HashMap.of(
        "type", "artifact",
        "account", "test",
        "reference", "ref1"
      ).toJavaMap()
    ).toJavaMap();

    final DeployCloudFoundryServiceDescription result = converter.convertDescription(input);

    assertThat(result.getSpace()).isEqualToComparingFieldByFieldRecursively(
      CloudFoundrySpace.builder().id("spaceID").name("space").organization(
        CloudFoundryOrganization.builder().id("orgID").name("org").build()).build());
    assertThat(result.getServiceAttributes()).isEqualToComparingFieldByFieldRecursively(
      new DeployCloudFoundryServiceDescription.ServiceAttributes()
        .setService("my-service")
        .setServiceName("my-service-name")
        .setServicePlan("my-service-plan")
        .setTags(Collections.singleton("tag1"))
        .setParameterMap(HashMap.<String, Object>of(
          "foo", "bar"
        ).toJavaMap())
    );
  }

  @Test
  void convertDescriptionWithUserProvidedInput() {
    final Map input = HashMap.of(
      "credentials", "test",
      "region", "org > space",
      "manifest", HashMap.of(
        "type", "userProvided",
        "serviceName", "userProvidedServiceName",
        "tags", List.of(
          "my-tag"
        ).asJava(),
        "syslogDrainUrl", "http://syslogDrainUrl.io",
        "credentials", "{\"foo\": \"bar\"}",
        "routeServiceUrl", "http://routeServiceUrl.io"
      ).toJavaMap()
    ).toJavaMap();

    final DeployCloudFoundryServiceDescription result = converter.convertDescription(input);
    assertThat(result.getServiceAttributes()).isNull();
    assertThat(result.getUserProvidedServiceAttributes()).isEqualToComparingFieldByFieldRecursively(
      new DeployCloudFoundryServiceDescription.UserProvidedServiceAttributes()
        .setServiceName("userProvidedServiceName")
        .setSyslogDrainUrl("http://syslogDrainUrl.io")
        .setRouteServiceUrl("http://routeServiceUrl.io")
        .setTags(Collections.singleton("my-tag"))
        .setCredentialsMap(HashMap.<String, Object>of(
          "foo", "bar"
        ).toJavaMap())
    );
  }

  @Test
  void convertDescriptionWithUserProvidedInputWithoutCredentials() {
    final Map input = HashMap.of(
      "credentials", "test",
      "region", "org > space",
      "manifest", HashMap.of(
        "type", "userProvided",
        "serviceName", "userProvidedServiceName",
        "tags", List.of(
          "my-tag"
        ).asJava(),
        "syslogDrainUrl", "http://syslogDrainUrl.io",
        "routeServiceUrl", "http://routeServiceUrl.io"
      ).toJavaMap()
    ).toJavaMap();

    final DeployCloudFoundryServiceDescription result = converter.convertDescription(input);
    assertThat(result.getServiceAttributes()).isNull();
    assertThat(result.getUserProvidedServiceAttributes()).isEqualToComparingFieldByFieldRecursively(
      new DeployCloudFoundryServiceDescription.UserProvidedServiceAttributes()
        .setServiceName("userProvidedServiceName")
        .setSyslogDrainUrl("http://syslogDrainUrl.io")
        .setRouteServiceUrl("http://routeServiceUrl.io")
        .setTags(Collections.singleton("my-tag"))
    );
  }
}
