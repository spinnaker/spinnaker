/*
 * Copyright 2019 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.google.deploy.converters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.clouddriver.google.compute.GoogleComputeApiFactory;
import com.netflix.spinnaker.clouddriver.google.deploy.description.SetStatefulDiskDescription;
import com.netflix.spinnaker.clouddriver.google.deploy.ops.SetStatefulDiskAtomicOperation;
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider;
import com.netflix.spinnaker.clouddriver.google.security.FakeGoogleCredentials;
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SetStatefulDiskAtomicOperationConverterTest {

  private static final String ACCOUNT_NAME = "spinnaker-account";
  private static final String SERVER_GROUP_NAME = "spinnaker-test-v000";
  private static final String REGION = "us-central1";
  private static final String DEVICE_NAME = "spinnaker-test-v000-001";

  SetStatefulDiskAtomicOperationConverter converter;

  @BeforeEach
  public void setUp() {
    GoogleClusterProvider clusterProvider = mock(GoogleClusterProvider.class);
    GoogleComputeApiFactory serverGroupManagersFactory = mock(GoogleComputeApiFactory.class);
    converter =
        new SetStatefulDiskAtomicOperationConverter(clusterProvider, serverGroupManagersFactory);

    CredentialsRepository credentialsRepository = mock(CredentialsRepository.class);
    GoogleNamedAccountCredentials accountCredentials =
        new GoogleNamedAccountCredentials.Builder()
            .name(ACCOUNT_NAME)
            .credentials(new FakeGoogleCredentials())
            .build();
    when(credentialsRepository.getOne(any())).thenReturn(accountCredentials);
    converter.setCredentialsRepository(credentialsRepository);
  }

  @Test
  public void testConvertDescription() {
    Map<String, String> input = new HashMap<>();
    input.put("accountName", ACCOUNT_NAME);
    input.put("serverGroupName", SERVER_GROUP_NAME);
    input.put("region", REGION);
    input.put("deviceName", DEVICE_NAME);
    SetStatefulDiskDescription description = converter.convertDescription(input);

    assertThat(description.getAccount()).isEqualTo(ACCOUNT_NAME);
    assertThat(description.getServerGroupName()).isEqualTo(SERVER_GROUP_NAME);
    assertThat(description.getRegion()).isEqualTo(REGION);
    assertThat(description.getDeviceName()).isEqualTo(DEVICE_NAME);
  }

  @Test
  public void testConvertOperation() {
    Map<String, String> input = new HashMap<>();
    input.put("accountName", ACCOUNT_NAME);
    input.put("serverGroupName", SERVER_GROUP_NAME);
    input.put("region", REGION);
    input.put("deviceName", DEVICE_NAME);
    SetStatefulDiskAtomicOperation operation = converter.convertOperation(input);

    SetStatefulDiskDescription description = operation.getDescription();
    assertThat(description.getAccount()).isEqualTo(ACCOUNT_NAME);
    assertThat(description.getServerGroupName()).isEqualTo(SERVER_GROUP_NAME);
    assertThat(description.getRegion()).isEqualTo(REGION);
    assertThat(description.getDeviceName()).isEqualTo(DEVICE_NAME);
  }
}
