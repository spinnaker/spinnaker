/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pollers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper;
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import retrofit2.Call;
import retrofit2.mock.Calls;

class AccountCacheTest {

  private final OortService oortService = mock(OortService.class);
  private final RetrySupport retrySupport = mock(RetrySupport.class);

  private final AccountCache accountCache =
      new AccountCache(
          mock(NotificationClusterLock.class),
          OrcaObjectMapper.getInstance(),
          oortService,
          retrySupport,
          new NoopRegistry());

  @BeforeEach
  void singleAttemptRetries() {
    when(retrySupport.retry(any(), anyInt(), anyLong(), anyBoolean()))
        .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(0).get());
  }

  @Test
  void resolvesAccountEnvironmentsFromClouddriverCredentials() {
    Call<List<Map<String, Object>>> call =
        Calls.response(
            List.of(
                Map.of(
                    "name", "test-account",
                    "cloudProvider", "aws",
                    "environment", "test",
                    "type", "aws")));
    when(oortService.getCredentials(true)).thenReturn(call);

    assertThat(accountCache.getEnvironment("test-account")).isEqualTo("test");
    assertThat(accountCache.getEnvironment("other-account")).isEqualTo("unknown");
    assertThat(call.isExecuted()).isTrue();
  }

  @Test
  void reportsUnknownEnvironmentWhenCredentialsCannotBeFetched() {
    Call<List<Map<String, Object>>> call =
        Calls.failure(new IOException("clouddriver unavailable"));
    when(oortService.getCredentials(true)).thenReturn(call);

    assertThat(accountCache.getEnvironment("test-account")).isEqualTo("unknown");
    assertThat(call.isExecuted()).isTrue();
  }
}
