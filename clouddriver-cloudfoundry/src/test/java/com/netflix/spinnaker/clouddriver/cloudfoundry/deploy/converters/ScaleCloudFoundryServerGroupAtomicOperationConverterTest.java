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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.MockCloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.ScaleCloudFoundryServerGroupDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryOrganization;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import com.netflix.spinnaker.clouddriver.cloudfoundry.security.CloudFoundryCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.security.MapBackedAccountCredentialsRepository;
import io.vavr.collection.HashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

class ScaleCloudFoundryServerGroupAtomicOperationConverterTest {

  private final CloudFoundryClient cloudFoundryClient = new MockCloudFoundryClient();
  {
    when(cloudFoundryClient.getOrganizations().findByName(any()))
      .thenAnswer((Answer<Optional<CloudFoundryOrganization>>) invocation -> {
        Object[] args = invocation.getArguments();
        return Optional.of(CloudFoundryOrganization.builder()
          .id(args[0].toString() + "-guid").name(args[0].toString()).build());
      });

    when(cloudFoundryClient.getOrganizations().findSpaceByRegion(any()))
      .thenReturn(Optional.of(CloudFoundrySpace.builder().build()));
  }

  private final CloudFoundryCredentials cloudFoundryCredentials = new CloudFoundryCredentials(
    "test", "", "", "", "", "", "") {
    public CloudFoundryClient getClient() {
      return cloudFoundryClient;
    }
  };

  private final AccountCredentialsRepository accountCredentialsRepository = new MapBackedAccountCredentialsRepository();
  {
    accountCredentialsRepository.update("test", cloudFoundryCredentials);
  }

  private final AccountCredentialsProvider accountCredentialsProvider =
    new DefaultAccountCredentialsProvider(accountCredentialsRepository);

  private final ScaleCloudFoundryServerGroupAtomicOperationConverter converter =
    new ScaleCloudFoundryServerGroupAtomicOperationConverter(null);

  @BeforeEach
  void initializeClassUnderTest() {
    converter.setAccountCredentialsProvider(accountCredentialsProvider);
    converter.setObjectMapper(new ObjectMapper());
  }

  @Test
  void convertDescription() {
    final Map input = HashMap.of(
      "credentials", "test",
      "region", "org > space",
      "capacity",  HashMap.of(
        "desired", 15,
        "min", 12,
        "max", 61
      ).toJavaMap(),
      "diskQuota", 1027,
      "memory", 10249
    ).toJavaMap();

    final ScaleCloudFoundryServerGroupDescription result = converter.convertDescription(input);

    assertThat(result.getCapacity().getDesired()).isEqualTo(15);
    assertThat(result.getDiskQuota()).isEqualTo(1027);
    assertThat(result.getMemory()).isEqualTo(10249);
  }

  @Test
  void convertDescriptionMissingFields() {
    final Map input = HashMap.of(
      "credentials", "test",
      "region", "org > space",
      "capacity",  HashMap.of(
        "desired", 215,
        "min", 12,
        "max", 61
      ).toJavaMap()
    ).toJavaMap();

    final ScaleCloudFoundryServerGroupDescription result = converter.convertDescription(input);

    assertThat(result.getCapacity().getDesired()).isEqualTo(215);
    assertThat(result.getDiskQuota()).isNull();
    assertThat(result.getMemory()).isNull();
  }
}
