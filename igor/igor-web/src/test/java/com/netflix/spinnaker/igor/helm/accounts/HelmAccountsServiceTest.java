/*
 * Copyright 2025 OpsMx, Inc.
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

package com.netflix.spinnaker.igor.helm.accounts;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.netflix.spinnaker.igor.config.HelmConverterFactory;
import com.netflix.spinnaker.igor.util.RetrofitUtils;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import java.util.List;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import retrofit2.Retrofit;

public class HelmAccountsServiceTest {

  @RegisterExtension
  static WireMockExtension wmHelmAccounts =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  static HelmAccountsService helmAccountsService;

  @BeforeAll
  public static void setup() {
    helmAccountsService =
        new Retrofit.Builder()
            .baseUrl(RetrofitUtils.getBaseUrl(wmHelmAccounts.baseUrl()))
            .client(new OkHttpClient())
            .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
            .addConverterFactory(new HelmConverterFactory())
            .build()
            .create(HelmAccountsService.class);
  }

  @Test
  public void testGetAllAccounts_with_additionalFields() {
    // Helm account with an addition field "type"
    String expectedResponse =
        "[{\"name\":\"acc1\",\"types\":[\"type1\",\"type2\"],\"type\":\"helm\"}]";

    wmHelmAccounts.stubFor(
        WireMock.get("/artifacts/credentials").willReturn(aResponse().withBody(expectedResponse)));

    List<ArtifactAccount> accounts =
        Retrofit2SyncCall.execute(helmAccountsService.getAllAccounts());

    assertThat(accounts).hasSize(1);
    assertThat(accounts.get(0).name).isEqualTo("acc1");
    assertThat(accounts.get(0).types).containsExactly("type1", "type2");
  }
}
