/*
 * Copyright 2023 Armory, Inc.
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

package com.netflix.kayenta.signalfx.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.kayenta.retrofit.config.RetrofitClientFactory;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.security.MapBackedAccountCredentialsRepository;
import com.squareup.okhttp.OkHttpClient;
import java.util.List;
import org.junit.jupiter.api.Test;

public class SignalFxConfigLoadTest {

  private AccountCredentialsRepository accountRepo = new MapBackedAccountCredentialsRepository();

  @Test
  public void testThatConfigLoadFromAccountRendersCorrectly() {
    SignalFxConfigurationProperties signalFxConfigurationProperties =
        mock(SignalFxConfigurationProperties.class);
    when(signalFxConfigurationProperties.getAccounts())
        .thenReturn(List.of(new SignalFxManagedAccount().setName("Test-account")));
    RetrofitClientFactory mockRetrofitFactory = mock(RetrofitClientFactory.class);
    OkHttpClient mockOkHttpFactory = mock(OkHttpClient.class);
    new SignalFxConfiguration()
        .signalFxMetricService(
            signalFxConfigurationProperties, mockRetrofitFactory, mockOkHttpFactory, accountRepo);
    assertEquals("signalfx", accountRepo.getRequiredOne("Test-account").getType());
    assertEquals(
        List.of(AccountCredentials.Type.METRICS_STORE),
        accountRepo.getRequiredOne("Test-account").getSupportedTypes());
  }
}
