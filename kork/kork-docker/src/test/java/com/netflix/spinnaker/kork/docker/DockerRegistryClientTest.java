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

package com.netflix.spinnaker.kork.docker;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.netflix.spinnaker.kork.docker.model.DockerBearerToken;
import com.netflix.spinnaker.kork.docker.model.DockerRegistryCatalog;
import com.netflix.spinnaker.kork.docker.model.DockerRegistryTags;
import com.netflix.spinnaker.kork.docker.service.DockerBearerTokenService;
import com.netflix.spinnaker.kork.docker.service.DockerRegistryClient;
import com.netflix.spinnaker.kork.docker.service.RegistryService;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import java.util.Arrays;
import java.util.Map;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@SpringBootTest(classes = {DockerBearerTokenService.class})
public class DockerRegistryClientTest {

  @RegisterExtension
  static WireMockExtension wmDockerRegistry =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  static RegistryService dockerRegistryService;
  @MockBean DockerBearerTokenService dockerBearerTokenService;
  DockerBearerTokenService dockerBearerTokenServiceUnauthenticated =
      Mockito.mock(DockerBearerTokenService.class);

  static DockerRegistryClient dockerRegistryClient;
  static DockerRegistryClient dockerRegistryClient2;

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
              "filtered/repo-e-1"
            });
    tagsResponseString = objectMapper.writeValueAsString(tagsResponse);
    tagsSecondResponseString = tagsResponseString.replaceAll("1", "2");
    tagsThirdResponseString = tagsResponseString.replaceAll("1", "3");

    catalogResponseString = objectMapper.writeValueAsString(catalogResponse);
    catalogSecondResponseString = catalogResponseString.replaceAll("1", "2");
    catalogThirdResponseString = catalogResponseString.replaceAll("1", "3");

    DockerBearerToken bearerToken = new DockerBearerToken();
    bearerToken.setToken("someToken");
    bearerToken.setAccessToken("someToken");
    Mockito.when(dockerBearerTokenService.getToken(anyString())).thenReturn(bearerToken);
    Mockito.when(dockerBearerTokenServiceUnauthenticated.getToken(anyString(), anyString()))
        .thenReturn(bearerToken);

    dockerRegistryService = buildService(RegistryService.class, wmDockerRegistry.baseUrl());
    dockerRegistryClient =
        new DockerRegistryClient(
            wmDockerRegistry.baseUrl(), 5, "", "", dockerRegistryService, dockerBearerTokenService);
    dockerRegistryClient2 =
        new DockerRegistryClient(
            wmDockerRegistry.baseUrl(),
            5,
            "",
            "",
            dockerRegistryService,
            dockerBearerTokenServiceUnauthenticated);
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

  @Test
  public void testDockerRegistryShouldValidateThatItIsPointinAtAV2endpoint() {
    wmDockerRegistry.stubFor(
        WireMock.get(urlMatching("/v2/"))
            .willReturn(aResponse().withStatus(HttpStatus.OK.value()).withBody("{}")));
    assertDoesNotThrow(
        () -> {
          dockerRegistryClient.checkV2Availability();
        });
    wmDockerRegistry.verify(1, WireMock.getRequestedFor(urlMatching("/v2/")));
    wmDockerRegistry.verify(
        1,
        WireMock.getRequestedFor(urlMatching("/v2/"))
            .withHeader("User-Agent", WireMock.containing("Spinnaker")));
  }

  @Test
  public void testDockerRegistryShouldFilterRepositoriesByRegex() {
    wmDockerRegistry.stubFor(
        WireMock.get(urlMatching("/v2/_catalog\\?n=5"))
            .willReturn(
                aResponse().withStatus(HttpStatus.OK.value()).withBody(catalogResponseString)));

    DockerRegistryCatalog dockerRegistryCatalog = dockerRegistryClient.getCatalog();
    assertEquals(5, dockerRegistryCatalog.getRepositories().size());

    DockerRegistryClient client =
        new DockerRegistryClient(
            wmDockerRegistry.baseUrl(),
            5,
            "",
            "filtered\\/.*",
            dockerRegistryService,
            dockerBearerTokenService);
    DockerRegistryCatalog dockerRegistryCatalogFiltered = client.getCatalog();
    assertEquals(1, dockerRegistryCatalogFiltered.getRepositories().size());
    assertEquals("filtered/repo-e-1", dockerRegistryCatalogFiltered.getRepositories().get(0));
  }

  @Test
  public void DockerRegistryClientShouldBeAbleToToFetchDigest() {
    String manifestResponseString =
        "{\n"
            + "    \"schemaVersion\": 2,\n"
            + "    \"mediaType\": \"application/vnd.docker.distribution.manifest.v2+json\",\n"
            + "    \"config\": {\n"
            + "        \"mediaType\": \"application/vnd.docker.container.image.v1+json\",\n"
            + "        \"size\": 2578,\n"
            + "        \"digest\": \"sha256:af2c053ebf8b22cbae434fe297c52aaf14c9ae72598aed25df03e6281644b500\"\n"
            + "    },\n"
            + "    \"layers\": [\n"
            + "        {\n"
            + "            \"mediaType\": \"application/vnd.docker.image.rootfs.diff.tar.gzip\",\n"
            + "            \"size\": 2387850,\n"
            + "            \"digest\": \"sha256:c1e54eec4b5786500c19795d1fc604aa7302aee307edfe0554a5c07108b77d48\"\n"
            + "        },\n"
            + "        {\n"
            + "            \"mediaType\": \"application/vnd.docker.image.rootfs.diff.tar.gzip\",\n"
            + "            \"size\": 184,\n"
            + "            \"digest\": \"sha256:83b840425d8ae4740536e990e5c5aedcc0bce060c52cf9f266459630b96c91e8\"\n"
            + "        },\n"
            + "        {\n"
            + "            \"mediaType\": \"application/vnd.docker.image.rootfs.diff.tar.gzip\",\n"
            + "            \"size\": 5872586,\n"
            + "            \"digest\": \"sha256:79ea61ba90a3ea63704d7b095d45c79f19573a241123b23455a8609c7a6347af\"\n"
            + "        },\n"
            + "        {\n"
            + "            \"mediaType\": \"application/vnd.docker.image.rootfs.diff.tar.gzip\",\n"
            + "            \"size\": 5872586,\n"
            + "            \"digest\": \"sha256:79ea61ba90a3ea63704d7b095d45c79f19573a241123b23455a8609c7a6347af\"\n"
            + "        }\n"
            + "    ]\n"
            + "}";

    wmDockerRegistry.stubFor(
        WireMock.get(urlMatching("/v2/library/nginx/manifests/1.0.0"))
            .willReturn(
                aResponse().withStatus(HttpStatus.OK.value()).withBody(manifestResponseString)));

    String digest = dockerRegistryClient.getConfigDigest("library/nginx", "1.0.0");
    assertEquals("sha256:af2c053ebf8b22cbae434fe297c52aaf14c9ae72598aed25df03e6281644b500", digest);
  }

  @Test
  public void DockerRegistryClientShouldBeAbleToToFetchTheConfigLayer() {
    String configLayerResponseString =
        "{\n"
            + "    \"architecture\": \"amd64\",\n"
            + "    \"config\": {\n"
            + "        \"ExposedPorts\": {\n"
            + "            \"80/tcp\": {}\n"
            + "        },\n"
            + "        \"Env\": [\n"
            + "            \"PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin\",\n"
            + "            \"NGINX_VERSION=1.28.0\",\n"
            + "            \"NJS_VERSION=0.8.10\",\n"
            + "            \"NJS_RELEASE=1~bookworm\",\n"
            + "            \"PKG_RELEASE=1~bookworm\",\n"
            + "            \"DYNPKG_RELEASE=1~bookworm\"\n"
            + "        ],\n"
            + "        \"Entrypoint\": [\n"
            + "            \"/docker-entrypoint.sh\"\n"
            + "        ],\n"
            + "        \"Cmd\": [\n"
            + "            \"nginx\",\n"
            + "            \"-g\",\n"
            + "            \"daemon off;\"\n"
            + "        ],\n"
            + "        \"Labels\": {\n"
            + "            \"maintainer\": \"NGINX Docker Maintainers <docker-maint@nginx.com>\",\n"
            + "            \"commitId\" : \"b48e2cf960de545597411c99ec969e47a7635ba3\"\n"
            + "        },\n"
            + "        \"StopSignal\": \"SIGQUIT\"\n"
            + "    },\n"
            + "    \"created\": \"2025-04-23T18:00:49Z\",\n"
            + "    \"history\": [\n"
            + "    ],\n"
            + "    \"os\": \"linux\",\n"
            + "    \"rootfs\": {\n"
            + "        \"type\": \"layers\",\n"
            + "        \"diff_ids\": [\n"
            + "            \"sha256:6c4c763d22d0c5f9b2c5901dfa667fbbc4713cee6869336b8fd5022185071f1c\",\n"
            + "            \"sha256:9f46bafac0d08ae71ba1cded69760cba6ac8f647b0c643fc8bee54bf66cb172b\",\n"
            + "            \"sha256:9aa50fe684c520cc1d48f7086fd491637e5eeee9315a1d2230fdfe1d67145379\",\n"
            + "            \"sha256:1097e804b7e9ff52048e9dd91ad691859d038d0dd305df3bd505070332da9594\",\n"
            + "            \"sha256:50495e2ba6dcb6ef54317ab717758e73d685b60183911f45e60c4d0d65a8ba1c\",\n"
            + "            \"sha256:b6ec55b719dc8f564e5749f53fe4f07c141a207fe72b0b019703d91d883a3285\",\n"
            + "            \"sha256:5a905c85a1e62e0d7be06acb90957b7f796be944a4864de79fcb7438b5169b00\",\n"
            + "            \"sha256:c522020b6a4645cdf93e4f0c1bd7af25b8f74f3281701fbb0b759fef1b6ccd16\"\n"
            + "        ]\n"
            + "    }\n"
            + "}";

    wmDockerRegistry.stubFor(
        WireMock.get(
                urlMatching(
                    "/v2/library/nginx/blobs/sha256:af2c053ebf8b22cbae434fe297c52aaf14c9ae72598aed25df03e6281644b500"))
            .willReturn(
                aResponse().withStatus(HttpStatus.OK.value()).withBody(configLayerResponseString)));

    Map content =
        dockerRegistryClient.getDigestContent(
            "library/nginx",
            "sha256:af2c053ebf8b22cbae434fe297c52aaf14c9ae72598aed25df03e6281644b500");
    Map configLayer = (Map) content.get("config");
    assertEquals("amd64", content.get("architecture"));
    assertNotNull(configLayer.get("Labels"));
    assertEquals(
        "{maintainer=NGINX Docker Maintainers <docker-maint@nginx.com>, commitId=b48e2cf960de545597411c99ec969e47a7635ba3}",
        configLayer.get("Labels").toString());
  }

  @Test
  public void dockerRegistryClientShouldHonorTheAuthenticateHeader() {
    String authenticateDetails =
        "realm=\"https://auth.docker.io/token\",service=\"registry.docker.io\",scope=\"repository:library/ubuntu:pull\"";
    String repository = "library/ubuntu";

    DockerBearerToken bearerToken = new DockerBearerToken();
    bearerToken.setToken("access-token");
    bearerToken.setAccessToken("bearer-token");

    dockerRegistryClient2.request(
        () -> {
          throw makeSpinnakerHttpException(authenticateDetails);
        },
        (String token) -> null,
        repository);

    Mockito.verify(dockerBearerTokenServiceUnauthenticated, Mockito.times(1))
        .getToken(Mockito.eq(repository), Mockito.eq(authenticateDetails));
  }

  public static SpinnakerHttpException makeSpinnakerHttpException(String authenticateDetails) {
    String url = "https://some-url";
    String BearerString = "Bearer " + authenticateDetails;

    okhttp3.Headers headers =
        new okhttp3.Headers.Builder().add("www-authenticate", BearerString).build();

    Response retrofit2Response =
        Response.error(
            ResponseBody.create(
                MediaType.parse("application/json"), "{ \"message\": \"arbitrary message\" }"),
            new okhttp3.Response.Builder()
                .code(HttpStatus.UNAUTHORIZED.value())
                .message("authentication required")
                .protocol(Protocol.HTTP_1_1)
                .request(new Request.Builder().url(url).build())
                .headers(headers)
                .build());

    Retrofit retrofit =
        new Retrofit.Builder()
            .baseUrl(url)
            .addConverterFactory(JacksonConverterFactory.create())
            .build();

    return new SpinnakerHttpException(retrofit2Response, retrofit);
  }
}
