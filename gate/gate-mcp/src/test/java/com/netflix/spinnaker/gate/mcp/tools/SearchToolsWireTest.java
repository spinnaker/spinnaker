/*
 * Copyright 2026 Netflix, Inc.
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

package com.netflix.spinnaker.gate.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.gate.services.internal.ClouddriverService;
import com.netflix.spinnaker.gate.services.internal.ClouddriverServiceSelector;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

/**
 * Verifies {@link SearchTools} against a real HTTP server (via a genuine Retrofit-backed {@link
 * ClouddriverService}, not a Mockito mock of the interface) - this exercises the actual query
 * string Retrofit builds from {@code @Query}/{@code @QueryMap} annotations, which is exactly the
 * layer where Gate's real {@code /search} proxy silently drops multi-value {@code type} (see the
 * class javadoc on {@link SearchTools}). A pure interface mock would happily accept any Java
 * arguments and couldn't catch that class of bug; this confirms the request Gate actually sends
 * over the wire matches what clouddriver's {@code SearchController} expects.
 */
@ExtendWith(MockitoExtension.class)
class SearchToolsWireTest {

  @Mock private ClouddriverServiceSelector clouddriverServiceSelector;

  private MockWebServer server;
  private SearchTools searchTools;

  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start();

    Retrofit retrofit =
        new Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(JacksonConverterFactory.create(new ObjectMapper()))
            // Matches production wiring (kork's ServiceClientProvider): without this,
            // Retrofit2SyncCall.execute()
            // just returns a null body for non-2xx responses instead of throwing, since it does not
            // itself check
            // response.isSuccessful() - see Retrofit2SyncCall's source.
            .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
            .build();
    ClouddriverService realClouddriverService = retrofit.create(ClouddriverService.class);
    lenient().when(clouddriverServiceSelector.select()).thenReturn(realClouddriverService);

    searchTools = new SearchTools(clouddriverServiceSelector);
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
  }

  @Test
  void searchInfrastructureSendsExactlyOneTypeAndTheQuery() throws InterruptedException {
    server.enqueue(jsonResponse("[]"));

    searchTools.searchInfrastructure("myapp", "applications", null, null, null, null, null);

    RecordedRequest request = server.takeRequest();
    HttpUrl url = request.getRequestUrl();
    assertThat(request.getPath()).startsWith("/search");
    assertThat(url.queryParameter("q")).isEqualTo("myapp");
    assertThat(url.queryParameterValues("type")).containsExactly("applications");
    assertThat(server.getRequestCount()).isEqualTo(1);
  }

  @Test
  void searchInfrastructureForwardsPlatformAndFiltersAsQueryParams() throws InterruptedException {
    server.enqueue(jsonResponse("[]"));

    searchTools.searchInfrastructure(
        "myapp",
        "serverGroups",
        "aws",
        25,
        2,
        null,
        Map.of("account", "prod", "region", "us-east-1"));

    HttpUrl url = server.takeRequest().getRequestUrl();
    assertThat(url.queryParameter("platform")).isEqualTo("aws");
    assertThat(url.queryParameter("pageSize")).isEqualTo("25");
    assertThat(url.queryParameter("page")).isEqualTo("2");
    assertThat(url.queryParameter("account")).isEqualTo("prod");
    assertThat(url.queryParameter("region")).isEqualTo("us-east-1");
  }

  @Test
  void searchInfrastructureOmitsPageSizeWhenUnspecifiedSoClouddriverDefaultApplies()
      throws InterruptedException {
    server.enqueue(jsonResponse("[]"));

    searchTools.searchInfrastructure("myapp", "applications", null, null, null, null, null);

    HttpUrl url = server.takeRequest().getRequestUrl();
    assertThat(url.queryParameterNames()).doesNotContain("pageSize");
  }

  @Test
  void searchInfrastructureShortCircuitsShortQueriesWithoutHittingTheNetwork() {
    searchTools.searchInfrastructure("ab", "applications", null, null, null, null, null);

    assertThat(server.getRequestCount()).isEqualTo(0);
  }

  @Test
  void searchInfrastructureAllowsShortQueriesWhenExplicitlyAllowed() throws InterruptedException {
    server.enqueue(jsonResponse("[]"));

    searchTools.searchInfrastructure("ab", "applications", null, null, null, true, null);

    assertThat(server.getRequestCount()).isEqualTo(1);
    assertThat(server.takeRequest().getRequestUrl().queryParameter("q")).isEqualTo("ab");
  }

  @Test
  void searchAllTypesFiresOneRequestPerTypeAndMergesResults() throws InterruptedException {
    server.enqueue(jsonResponse("[{\"name\":\"myapp\"}]"));
    server.enqueue(jsonResponse("[{\"cluster\":\"myapp-main\"}]"));

    Map<String, Object> result =
        searchTools.searchAllTypes(
            "myapp", List.of("applications", "clusters"), null, null, null, null);

    assertThat(server.getRequestCount()).isEqualTo(2);
    RecordedRequest first = server.takeRequest();
    RecordedRequest second = server.takeRequest();
    assertThat(first.getRequestUrl().queryParameterValues("type")).containsExactly("applications");
    assertThat(second.getRequestUrl().queryParameterValues("type")).containsExactly("clusters");

    @SuppressWarnings("unchecked")
    Map<String, Object> resultsByType = (Map<String, Object>) result.get("resultsByType");
    assertThat(resultsByType).containsKeys("applications", "clusters");
    assertThat((List) resultsByType.get("applications")).hasSize(1);
    assertThat((List) resultsByType.get("clusters")).hasSize(1);
    assertThat(result).doesNotContainKey("errorsByType");
  }

  @Test
  void searchAllTypesDefaultsToDecksStandardCategoriesWhenNoneSpecified()
      throws InterruptedException {
    for (int i = 0; i < 7; i++) {
      server.enqueue(jsonResponse("[]"));
    }

    searchTools.searchAllTypes("myapp", null, null, null, null, null);

    assertThat(server.getRequestCount()).isEqualTo(7);
  }

  @Test
  void searchAllTypesIsolatesAPerTypeFailureRatherThanFailingEverything()
      throws InterruptedException {
    server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));
    server.enqueue(jsonResponse("[{\"name\":\"myapp\"}]"));

    Map<String, Object> result =
        searchTools.searchAllTypes(
            "myapp", List.of("applications", "clusters"), null, null, null, null);

    @SuppressWarnings("unchecked")
    Map<String, Object> resultsByType = (Map<String, Object>) result.get("resultsByType");
    @SuppressWarnings("unchecked")
    Map<String, Object> errorsByType = (Map<String, Object>) result.get("errorsByType");

    assertThat(errorsByType).containsKey("applications");
    assertThat((List) resultsByType.get("applications")).isEmpty();
    assertThat((List) resultsByType.get("clusters")).hasSize(1);
  }

  @Test
  void searchAllTypesShortCircuitsShortQueriesWithoutHittingTheNetwork() {
    Map<String, Object> result =
        searchTools.searchAllTypes(
            "ab", List.of("applications", "clusters"), null, null, null, null);

    assertThat(server.getRequestCount()).isEqualTo(0);
    @SuppressWarnings("unchecked")
    Map<String, Object> resultsByType = (Map<String, Object>) result.get("resultsByType");
    assertThat((List) resultsByType.get("applications")).isEmpty();
    assertThat((List) resultsByType.get("clusters")).isEmpty();
  }

  private static MockResponse jsonResponse(String body) {
    return new MockResponse().setBody(body).addHeader("Content-Type", "application/json");
  }
}
