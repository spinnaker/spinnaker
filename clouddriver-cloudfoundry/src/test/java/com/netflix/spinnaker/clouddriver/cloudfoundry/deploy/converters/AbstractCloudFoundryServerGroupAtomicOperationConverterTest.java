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

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.MockCloudFoundryClient;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundryOrganization;
import com.netflix.spinnaker.clouddriver.cloudfoundry.model.CloudFoundrySpace;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

class AbstractCloudFoundryServerGroupAtomicOperationConverterTest {
  private final CloudFoundryClient cloudFoundryClient = new MockCloudFoundryClient();
  private final DestroyCloudFoundryServerGroupAtomicOperationConverter converter = new DestroyCloudFoundryServerGroupAtomicOperationConverter();


  {
    when(cloudFoundryClient.getOrganizations().findByName(any()))
      .thenAnswer((Answer<Optional<CloudFoundryOrganization>>) invocation -> {
        Object[] args = invocation.getArguments();
        return Optional.of(CloudFoundryOrganization.builder()
          .id(args[0].toString() + "ID").name(args[0].toString()).build());
      });

    when(cloudFoundryClient.getApplications().findServerGroupId(any(), any()))
      .thenAnswer((Answer<String>) invocation -> {
        Object[] args = invocation.getArguments();

        if (args[0].equals("bad-servergroup-name")) {
          return null;
        } else {
          return "servergroup-id";
        }
      });
  }

  @Test
  void getServerGroupIdSuccess() {
    when(cloudFoundryClient.getOrganizations().findSpaceByRegion(any()))
      .thenReturn(Optional.of(
        CloudFoundrySpace.builder().build()
      ));
    assertThat(converter.getServerGroupId("server", "region > space", cloudFoundryClient)).isEqualTo("servergroup-id");
  }

  @Test
  void getServerGroupIdFindFails() {
    when(cloudFoundryClient.getOrganizations().findSpaceByRegion(any()))
      .thenReturn(Optional.of(
        CloudFoundrySpace.builder().build()
      ));
    assertThat(converter.getServerGroupId("bad-servergroup-name", "region > space", cloudFoundryClient)).isNull();
  }

  @Test
  void getServerGroupIdSpaceInvalid() {
    when(cloudFoundryClient.getOrganizations().findSpaceByRegion(any()))
      .thenReturn(Optional.empty());
    assertThat(converter.getServerGroupId("server", "region > region", cloudFoundryClient)).isNull();
  }
}
