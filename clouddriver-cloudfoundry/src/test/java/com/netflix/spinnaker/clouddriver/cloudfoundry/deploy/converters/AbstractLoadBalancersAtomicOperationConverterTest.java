/*
 * Copyright 2019 Pivotal, Inc.
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
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.MockCloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.RouteId;
import com.netflix.spinnaker.clouddriver.cloudfoundry.deploy.description.LoadBalancersDescription;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryDomain;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryLoadBalancer;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryOrganization;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import com.netflix.spinnaker.clouddriver.cloudfoundry.security.CloudFoundryCredentials;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
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

class AbstractLoadBalancersAtomicOperationConverterTest {
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
      if (args[0].equals("region")) {
        return null;
      }
      return CloudFoundrySpace.builder().id(args[1].toString() + "ID").name(args[1].toString())
        .organization(CloudFoundryOrganization.builder()
          .id(args[0].toString()).name(args[0].toString().replace("ID", "")).build()).build();
    });

    when(cloudFoundryClient.getRoutes().toRouteId(any())).thenAnswer((Answer<RouteId>) invocation -> {
      Object[] args = invocation.getArguments();
      if (args[0].equals("foo")) {
        return null;
      }
      return new RouteId("host", "index", null, "some-guid");
    });

    when(cloudFoundryClient.getRoutes().find(any(), any())).thenAnswer((Answer<CloudFoundryLoadBalancer>) invocation -> CloudFoundryLoadBalancer.builder()
      .host("host").path("index").domain(
        CloudFoundryDomain.builder().name("domain.com").build()
      ).build());
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

  private final TestAbstractLoadBalancersAtomicOperationConverter converter = new TestAbstractLoadBalancersAtomicOperationConverter();

  @BeforeEach
  void initializeClassUnderTest() {
    converter.setAccountCredentialsProvider(accountCredentialsProvider);
    converter.setObjectMapper(new ObjectMapper());
  }

  @Test
  void convertValidDescription() {
    final Map input = HashMap.of(
      "credentials", "test",
      "region", "org > space",
      "loadBalancerNames", List.of(
        "foo.host.com/index", "bar.host.com"
      ).asJava(),
      "serverGroupName", "serverGroupName"
    ).toJavaMap();

    final LoadBalancersDescription result = converter.convertDescription(input);

    assertThat(result.getRoutes()).isEqualTo(List.of(
      "foo.host.com/index", "bar.host.com"
    ).asJava());
    assertThat(result.getRegion()).isEqualTo("org > space");
  }

  @Test
  void convertWithRoutesNotFound() {
    final Map input = HashMap.of(
      "credentials", "test",
      "region", "region > region",
      "loadBalancerNames", Collections.EMPTY_LIST,
      "serverGroupName", "serverGroupName"
    ).toJavaMap();

    assertThrows(IllegalArgumentException.class, () -> converter.convertDescription(input));
  }

  private class TestAbstractLoadBalancersAtomicOperationConverter extends AbstractLoadBalancersAtomicOperationConverter {
    @Override
    public AtomicOperation convertOperation(Map input) {
      return null;
    }
  }
}
