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

package com.netflix.spinnaker.orca.clouddriver;

import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.kork.retrofit.util.CustomConverterFactory;
import java.util.Map;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import retrofit2.Retrofit;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class MortServiceTest {

  @RegisterExtension
  static WireMockExtension wmMort =
      WireMockExtension.newInstance().options(new WireMockConfiguration().dynamicPort()).build();

  private static MortService mortService;
  private final ObjectMapper mapper = JsonMapper.builder().build();

  @BeforeAll
  public static void setup() {
    mortService =
        new Retrofit.Builder()
            .baseUrl(wmMort.baseUrl())
            .client(new OkHttpClient())
            .addConverterFactory(CustomConverterFactory.create(JsonMapper.builder().build()))
            .build()
            .create(MortService.class);
  }

  @Test
  public void handle_Kubernetes_Complex_Description() throws JacksonException {
    JsonNode description =
        mapper.convertValue("{\"account\":null,\"app\":\"sg1\"}", JsonNode.class);
    Map<String, Object> sgMap =
        Map.of(
            "accountName",
            "account",
            "description",
            description,
            "name",
            "sg1",
            "region",
            "namespace",
            "type",
            "kubernetes");
    String sgAsString = mapper.writeValueAsString(sgMap);

    wmMort.stubFor(
        WireMock.get(urlMatching("/securityGroups/account/kubernetes/namespace/sg1"))
            .willReturn(WireMock.aResponse().withStatus(200).withBody(sgAsString)));

    MortService.SecurityGroup sg =
        Retrofit2SyncCall.execute(
            mortService.getSecurityGroup("account", "kubernetes", "sg1", "namespace"));

    assertThat(sg.getDescription()).isEqualTo("{\"account\":null,\"app\":\"sg1\"}");
  }

  @Test
  public void handle_normal_string_Description() throws JacksonException {
    Map<String, Object> sgMap =
        Map.of(
            "accountName",
            "account",
            "description",
            "simple description",
            "name",
            "sg1",
            "region",
            "namespace",
            "type",
            "kubernetes");
    String sgAsString = mapper.writeValueAsString(sgMap);

    wmMort.stubFor(
        WireMock.get(urlMatching("/securityGroups/account/kubernetes/namespace/sg1"))
            .willReturn(WireMock.aResponse().withStatus(200).withBody(sgAsString)));

    MortService.SecurityGroup sg =
        Retrofit2SyncCall.execute(
            mortService.getSecurityGroup("account", "kubernetes", "sg1", "namespace"));

    assertThat(sg.getDescription()).isEqualTo("simple description");
  }
}
