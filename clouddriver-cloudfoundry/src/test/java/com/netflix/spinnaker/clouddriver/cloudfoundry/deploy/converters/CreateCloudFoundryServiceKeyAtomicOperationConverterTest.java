/*
 * Copyright 2019 Pivotal, Inc.
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
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.CreateCloudFoundryServiceKeyDescription;
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
import org.junit.jupiter.api.Test;

class CreateCloudFoundryServiceKeyAtomicOperationConverterTest {
  private final CloudFoundryClient cloudFoundryClient = new MockCloudFoundryClient();
  private final CacheRepository cacheRepository = mock(CacheRepository.class);

  private CloudFoundrySpace cloudFoundrySpace =
      CloudFoundrySpace.builder()
          .id("space-guid")
          .name("space")
          .organization(CloudFoundryOrganization.builder().id("org-guid").name("org").build())
          .build();

  {
    when(cloudFoundryClient.getSpaces().findSpaceByRegion(any()))
        .thenReturn(Optional.of(cloudFoundrySpace));
  }

  private final CloudFoundryCredentials cloudFoundryCredentials =
      new CloudFoundryCredentials(
          "my-account",
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

  private final CredentialsRepository<CloudFoundryCredentials> credentialsRepository =
      new MapBackedCredentialsRepository<>(CloudFoundryProvider.PROVIDER_ID, null);

  {
    credentialsRepository.save(cloudFoundryCredentials);
  }

  @Test
  void convertDescriptionSucceeds() {
    CreateCloudFoundryServiceKeyAtomicOperationConverter converter =
        new CreateCloudFoundryServiceKeyAtomicOperationConverter();
    converter.setCredentialsRepository(credentialsRepository);

    String serviceKeyName = "service-key-name";
    String serviceInstanceName = "service-instance-name";
    String region = "org > space";
    Map input =
        HashMap.of(
                "credentials", cloudFoundryCredentials.getName(),
                "region", region,
                "serviceInstanceName", serviceInstanceName,
                "serviceKeyName", serviceKeyName)
            .toJavaMap();

    CreateCloudFoundryServiceKeyDescription expectedResult =
        (CreateCloudFoundryServiceKeyDescription)
            new CreateCloudFoundryServiceKeyDescription()
                .setServiceKeyName(serviceKeyName)
                .setServiceInstanceName(serviceInstanceName)
                .setSpace(cloudFoundrySpace)
                .setRegion(region)
                .setClient(cloudFoundryClient)
                .setCredentials(cloudFoundryCredentials);

    CreateCloudFoundryServiceKeyDescription result = converter.convertDescription(input);

    assertThat(result).isEqualToComparingFieldByFieldRecursively(expectedResult);
  }
}
