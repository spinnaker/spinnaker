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

package com.netflix.spinnaker.clouddriver.docker.registry.api.v2.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.ArgumentMatchers.anyString;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.auth.DockerBearerToken;
import com.netflix.spinnaker.clouddriver.docker.registry.api.v2.auth.DockerBearerTokenService;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import java.util.Arrays;
import java.util.Map;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@SpringBootTest(classes = {DockerBearerTokenService.class})
public class DockerRegistryClientTest {

  @RegisterExtension
  static WireMockExtension wmDockerRegistry =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  static DockerRegistryClient.DockerRegistryService dockerRegistryService;
  @MockBean DockerBearerTokenService dockerBearerTokenService;
  static DockerRegistryClient dockerRegistryClient;
  ObjectMapper objectMapper = new ObjectMapper();
  Map<String, Object> tagsResponse;
  String tagsResponseString;
  String tagsSecondResponseString;
  String tagsThirdResponseString;
  Map<String, Object> catalogResponse;
  String catalogResponseString;
  String catalogSecondResponseString;
  String catalogThirdResponseString;

  @BeforeEach
  public void init() throws JsonProcessingException {
    tagsResponse =
        Map.of(
            "name",
            "library/nginx",
            "tags",
            new String[] {"1", "1-alpine", "1-alpine-otel", "1-alpine-perl", "1-alpine-slim"});
    catalogResponse =
        Map.of(
            "repositories",
            new String[] {
              "library/repo-a-1",
              "library/repo-b-1",
              "library/repo-c-1",
              "library/repo-d-1",
              "library/repo-e-1"
            });
    tagsResponseString = objectMapper.writeValueAsString(tagsResponse);
    tagsSecondResponseString = tagsResponseString.replaceAll("1", "2");
    tagsThirdResponseString = tagsResponseString.replaceAll("1", "3");

    catalogResponseString = objectMapper.writeValueAsString(catalogResponse);
    catalogSecondResponseString = catalogResponseString.replaceAll("1", "2");
    catalogThirdResponseString = catalogResponseString.replaceAll("1", "3");

    DockerBearerToken bearerToken = new DockerBearerToken();
    bearerToken.setToken("someToken");
    bearerToken.setAccess_token("someToken");
    Mockito.when(dockerBearerTokenService.getToken(anyString())).thenReturn(bearerToken);
    dockerRegistryService =
        buildService(DockerRegistryClient.DockerRegistryService.class, wmDockerRegistry.baseUrl());
    dockerRegistryClient =
        new DockerRegistryClient(
            wmDockerRegistry.baseUrl(), 5, "", "", dockerRegistryService, dockerBearerTokenService);
  }

  private static <T> T buildService(Class<T> type, String baseUrl) {
    return new Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(new OkHttpClient())
        .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
        .addConverterFactory(JacksonConverterFactory.create())
        .build()
        .create(type);
  }

  @Test
  public void getTagsWithoutNextLink() {
    wmDockerRegistry.stubFor(
        WireMock.get(urlMatching("/v2/library/nginx/tags/list\\?n=5"))
            .willReturn(
                aResponse().withStatus(HttpStatus.OK.value()).withBody(tagsResponseString)));

    DockerRegistryTags dockerRegistryTags = dockerRegistryClient.getTags("library/nginx");
    String[] tags = (String[]) tagsResponse.get("tags");
    assertIterableEquals(Arrays.asList(tags), dockerRegistryTags.getTags());
  }

  @Test
  public void getTagsWithNextLink() {
    wmDockerRegistry.stubFor(
        WireMock.get(urlMatching("/v2/library/nginx/tags/list\\?n=5"))
            .willReturn(
                aResponse()
                    .withStatus(HttpStatus.OK.value())
                    .withHeader(
                        "link",
                        "</v2/library/nginx/tags/list?last=1-alpine-slim&n=5>; rel=\"next\"")
                    .withBody(tagsResponseString)));
    wmDockerRegistry.stubFor(
        WireMock.get(urlMatching("/v2/library/nginx/tags/list\\?last=1-alpine-slim&n=5"))
            .willReturn(
                aResponse()
                    .withStatus(HttpStatus.OK.value())
                    .withHeader(
                        "link",
                        // to test the logic when `?` is not present in the link header
                        "</v2/library/nginx/tags/list1>; rel=\"next\"")
                    .withBody(tagsSecondResponseString)));
    wmDockerRegistry.stubFor(
        WireMock.get(urlMatching("/v2/library/nginx/tags/list1\\?n=5"))
            .willReturn(
                aResponse().withStatus(HttpStatus.OK.value()).withBody(tagsThirdResponseString)));

    DockerRegistryTags dockerRegistryTags = dockerRegistryClient.getTags("library/nginx");
    assertEquals(15, dockerRegistryTags.getTags().size());
  }

  @Test
  public void getCatalogWithNextLink() {
    wmDockerRegistry.stubFor(
        WireMock.get(urlMatching("/v2/_catalog\\?n=5"))
            .willReturn(
                aResponse()
                    .withStatus(HttpStatus.OK.value())
                    .withHeader("link", "</v2/_catalog?last=repo1&n=5>; rel=\"next\"")
                    .withBody(catalogResponseString)));
    wmDockerRegistry.stubFor(
        WireMock.get(urlMatching("/v2/_catalog\\?last=repo1&n=5"))
            .willReturn(
                aResponse()
                    .withStatus(HttpStatus.OK.value())
                    .withHeader(
                        "link",
                        // to test the logic when `?` is not present in the link header
                        "</v2/_catalog1>; rel=\"next\"")
                    .withBody(catalogSecondResponseString)));
    wmDockerRegistry.stubFor(
        WireMock.get(urlMatching("/v2/_catalog1\\?n=5"))
            .willReturn(
                aResponse()
                    .withStatus(HttpStatus.OK.value())
                    .withBody(catalogThirdResponseString)));

    DockerRegistryCatalog dockerRegistryCatalog = dockerRegistryClient.getCatalog();
    assertEquals(15, dockerRegistryCatalog.getRepositories().size());
  }

  @Test
  public void getTagsWithNextLinkEncryptedAndEncoded() {
    String tagsListEndPointMinusQueryParams = "/v2/library/nginx/tags/list";
    String expectedEncodedParam = "Md1Woj%2FNOhjepFq7kPAr%2FEw%2FYxfcJoH9N52%2Blo3qAQ%3D%3D";

    wmDockerRegistry.stubFor(
        WireMock.get(urlMatching(tagsListEndPointMinusQueryParams + "\\?n=5"))
            .willReturn(
                aResponse()
                    .withStatus(HttpStatus.OK.value())
                    .withHeader(
                        "link",
                        "</v2/library/nginx/tags/list?last=Md1Woj%2FNOhjepFq7kPAr%2FEw%2FYxfcJoH9N52%2Blo3qAQ%3D%3D&n=5>; rel=\"next\"")
                    .withBody(tagsResponseString)));

    wmDockerRegistry.stubFor(
        WireMock.get(
                urlMatching(
                    tagsListEndPointMinusQueryParams + "\\?last=" + expectedEncodedParam + "&n=5"))
            .willReturn(
                aResponse().withStatus(HttpStatus.OK.value()).withBody(tagsSecondResponseString)));

    DockerRegistryTags dockerRegistryTags = dockerRegistryClient.getTags("library/nginx");
    assertThat(dockerRegistryTags.getTags()).hasSize(10);

    wmDockerRegistry.verify(
        1, getRequestedFor(urlMatching(tagsListEndPointMinusQueryParams + "\\?n=5")));
    wmDockerRegistry.verify(
        1,
        getRequestedFor(
            urlMatching(
                tagsListEndPointMinusQueryParams + "\\?last=" + expectedEncodedParam + "&n=5")));
  }

  @Test
  public void testTagsResponse_With_AdditionalFields() throws JsonProcessingException {
    Map<String, Object> tagsResponse =
        Map.of(
            "child",
            new String[] {},
            "manifest",
            Map.of(
                "sha256:ec1b05dxxxxxxxxxxxxxxxxxxxedb1d6a4",
                Map.of(
                    "mediaType",
                    "application/vnd.docker.distribution.manifest.v2+json",
                    "tag",
                    new String[] {"1.0.0", "2.0.1"})),
            "name",
            "library/nginx",
            "tags",
            new String[] {"1", "1-alpine", "1-alpine-otel", "1-alpine-perl", "1-alpine-slim"});

    wmDockerRegistry.stubFor(
        WireMock.get(urlMatching("/v2/library/nginx/tags/list\\?n=5"))
            .willReturn(
                aResponse()
                    .withStatus(HttpStatus.OK.value())
                    .withBody(objectMapper.writeValueAsString(tagsResponse))));

    DockerRegistryTags dockerRegistryTags = dockerRegistryClient.getTags("library/nginx");
    String[] tags = (String[]) tagsResponse.get("tags");
    assertIterableEquals(Arrays.asList(tags), dockerRegistryTags.getTags());
  }
}
