/*
 * Copyright 2025 OpsMx, Inc.
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

package com.netflix.spinnaker.fiat.roles.github;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.netflix.spinnaker.fiat.roles.github.client.GitHubClient;
import com.netflix.spinnaker.fiat.roles.github.model.Member;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import java.util.List;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public class GithubTeamsUserRolesProviderTest {
  @RegisterExtension
  static WireMockExtension wmGithub =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  GitHubClient gitHubClient;
  int port;
  String baseUrl = "http://localhost:PORT/api/v3/";

  @BeforeEach
  void setUp() {

    port = wmGithub.getPort();

    baseUrl = baseUrl.replaceFirst("PORT", String.valueOf(port));

    gitHubClient =
        new Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(new OkHttpClient())
            .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
            .addConverterFactory(JacksonConverterFactory.create())
            .build()
            .create(GitHubClient.class);
  }

  @Test
  void testBaseUrlWithMultipleSlashes() {
    wmGithub.stubFor(
        WireMock.get(urlEqualTo("/api/v3/orgs/org1/members?page=1&per_page=2"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(
                        "[{\"login\": \"foo\",\"id\": 18634546},{\"login\": \"bar\",\"id\": 202758929}]")));

    List<Member> members = Retrofit2SyncCall.execute(gitHubClient.getOrgMembers("org1", 1, 2));

    wmGithub.verify(
        1, WireMock.getRequestedFor(urlEqualTo("/api/v3/orgs/org1/members?page=1&per_page=2")));

    assertThat(members.size()).isEqualTo(2);
  }
}
