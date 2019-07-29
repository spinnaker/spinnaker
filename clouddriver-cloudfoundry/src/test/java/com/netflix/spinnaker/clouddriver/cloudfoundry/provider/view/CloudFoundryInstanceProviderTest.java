/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.clouddriver.cloudfoundry.provider.view;

import static com.netflix.spinnaker.clouddriver.cloudfoundry.provider.view.CloudFoundryInstanceProvider.LogsResourceType.APP;
import static com.netflix.spinnaker.clouddriver.cloudfoundry.provider.view.CloudFoundryInstanceProvider.LogsResourceType.TASK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.clouddriver.cloudfoundry.cache.CacheRepository;
import com.netflix.spinnaker.clouddriver.cloudfoundry.provider.view.CloudFoundryInstanceProvider.CloudFoundryConsoleOutputIdParameter;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import org.junit.jupiter.api.Test;

class CloudFoundryInstanceProviderTest {

  @Test
  void getConsoleOutput_withNonCloudFoundryAccount_returnsNull() {
    AccountCredentialsProvider credentialProvider = mock(AccountCredentialsProvider.class);
    when(credentialProvider.getCredentials(eq("account1")))
        .thenReturn(mock(AccountCredentials.class));

    CloudFoundryInstanceProvider provider =
        new CloudFoundryInstanceProvider(mock(CacheRepository.class), credentialProvider);

    assertThat(provider.getConsoleOutput("account1", "location", "task:jobId")).isNull();
  }

  @Test
  void cloudFoundryConsoleOutputIdParameter_fromString_validAppLogsId() {
    CloudFoundryConsoleOutputIdParameter param =
        CloudFoundryConsoleOutputIdParameter.fromString("app:12345:99");

    assertThat(param.getLogsResourceType()).isEqualTo(APP);
    assertThat(param.getGuid()).isEqualTo("12345");
    assertThat(param.getInstanceIndex()).isEqualTo(99);
  }

  @Test
  void cloudFoundryConsoleOutputIdParameter_fromString_validTaskLogsId() {
    CloudFoundryConsoleOutputIdParameter param =
        CloudFoundryConsoleOutputIdParameter.fromString("task:12345");

    assertThat(param.getLogsResourceType()).isEqualTo(TASK);
    assertThat(param.getGuid()).isEqualTo("12345");
    assertThat(param.getInstanceIndex()).isEqualTo(0);
  }

  @Test
  void cloudFoundryConsoleOutputIdParameter_fromString_ignoredTaskInstanceId() {
    CloudFoundryConsoleOutputIdParameter param =
        CloudFoundryConsoleOutputIdParameter.fromString("task:12345:1");

    assertThat(param.getLogsResourceType()).isEqualTo(TASK);
    assertThat(param.getGuid()).isEqualTo("12345");
    assertThat(param.getInstanceIndex()).isEqualTo(0);
  }

  @Test
  void cloudFoundryConsoleOutputIdParameter_fromString_invalidType() {
    assertThrows(
        IllegalArgumentException.class,
        () -> CloudFoundryConsoleOutputIdParameter.fromString("invalid:12345:1"));
  }

  @Test
  void cloudFoundryConsoleOutputIdParameter_fromString_appLogsIdMissingInstanceIndex() {
    assertThrows(
        IllegalArgumentException.class,
        () -> CloudFoundryConsoleOutputIdParameter.fromString("app:12345"));
  }
}
