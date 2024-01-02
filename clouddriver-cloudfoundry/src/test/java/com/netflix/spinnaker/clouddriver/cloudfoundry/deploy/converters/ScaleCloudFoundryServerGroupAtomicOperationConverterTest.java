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

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.clouddriver.cloudfoundry.cache.CacheRepository;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.MockCloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.config.CloudFoundryConfigurationProperties;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.ScaleCloudFoundryServerGroupDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryOrganization;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import com.netflix.spinnaker.clouddriver.cloudfoundry.provider.CloudFoundryProvider;
import com.netflix.spinnaker.clouddriver.cloudfoundry.security.CloudFoundryCredentials;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.credentials.MapBackedCredentialsRepository;
import io.vavr.collection.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

class ScaleCloudFoundryServerGroupAtomicOperationConverterTest {

  private final CloudFoundryClient cloudFoundryClient = new MockCloudFoundryClient();
  private final CacheRepository cacheRepository = mock(CacheRepository.class);

  private final CloudFoundryCredentials cloudFoundryCredentials =
      new CloudFoundryCredentials(
          "test",
          "managerUri",
          "metricsUri",
          "apiHost",
          "username",
          "password",
          "environment",
          false,
          false,
          500,
          cacheRepository,
          null,
          ForkJoinPool.commonPool(),
          emptyMap(),
          new OkHttpClient(),
          new CloudFoundryConfigurationProperties.ClientConfig(),
          new CloudFoundryConfigurationProperties.LocalCacheConfig()) {
        public CloudFoundryClient getClient() {
          return cloudFoundryClient;
        }
      };

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
        .thenReturn(Optional.of(CloudFoundrySpace.builder().build()));
  }

  private final CredentialsRepository<CloudFoundryCredentials> credentialsRepository =
      new MapBackedCredentialsRepository<>(CloudFoundryProvider.PROVIDER_ID, null);

  {
    credentialsRepository.save(cloudFoundryCredentials);
  }

  private final ScaleCloudFoundryServerGroupAtomicOperationConverter converter =
      new ScaleCloudFoundryServerGroupAtomicOperationConverter(null);

  @BeforeEach
  void initializeClassUnderTest() {
    converter.setCredentialsRepository(credentialsRepository);
  }

  @Test
  void convertDescription() {
    final Map input =
        HashMap.of(
                "credentials",
                "test",
                "region",
                "org > space",
                "capacity",
                HashMap.of(
                        "desired", 15,
                        "min", 12,
                        "max", 61)
                    .toJavaMap(),
                "diskQuota",
                1027,
                "memory",
                10249)
            .toJavaMap();

    final ScaleCloudFoundryServerGroupDescription result = converter.convertDescription(input);

    assertThat(result.getCapacity().getDesired()).isEqualTo(15);
    assertThat(result.getDiskQuota()).isEqualTo(1027);
    assertThat(result.getMemory()).isEqualTo(10249);
  }

  @Test
  void convertDescriptionMissingFields() {
    final Map input =
        HashMap.of(
                "credentials",
                "test",
                "region",
                "org > space",
                "capacity",
                HashMap.of(
                        "desired", 215,
                        "min", 12,
                        "max", 61)
                    .toJavaMap())
            .toJavaMap();

    final ScaleCloudFoundryServerGroupDescription result = converter.convertDescription(input);

    assertThat(result.getCapacity().getDesired()).isEqualTo(215);
    assertThat(result.getDiskQuota()).isNull();
    assertThat(result.getMemory()).isNull();
  }
}
