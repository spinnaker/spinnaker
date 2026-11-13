/*
 * Copyright 2026 DoorDash, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.expressions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.okhttp.Retrofit2EncodeCorrectionInterceptor;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.model.Manifest;
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import retrofit2.mock.Calls;

@ExtendWith(MockitoExtension.class)
class CloudResourcesExpressionFunctionProviderTest {

  @Mock private OortService oortService;

  @BeforeEach
  void setup() {
    new CloudResourcesExpressionFunctionProvider(oortService);
  }

  @Test
  void getK8sManifestsByKindsAndLabels_returnsManifests_whenOortServiceReturnsManifests() {
    // Given
    List<Manifest> expectedManifests =
        Arrays.asList(
            createManifest("manifest-1"),
            createManifest("manifest-2"),
            createManifest("manifest-3"));
    Map<String, String> labels = createLabels();
    when(oortService.getManifestsByKindsAndLabels(
            "test-account", "test-namespace", "Deployment", labels))
        .thenReturn(Calls.response(expectedManifests));

    // When
    List<Manifest> result =
        CloudResourcesExpressionFunctionProvider.getK8sManifestsByKindsAndLabels(
            "Deployment", "test-account", "test-namespace", labels);

    // Then
    assertEquals(expectedManifests, result);
    verify(oortService)
        .getManifestsByKindsAndLabels("test-account", "test-namespace", "Deployment", labels);
  }

  @Test
  void getK8sManifestsByKindsAndLabels_returnsEmptyList_whenOortServiceReturnsEmptyList() {
    // Given
    Map<String, String> labels = createLabels();
    when(oortService.getManifestsByKindsAndLabels(
            "test-account", "test-namespace", "Service", labels))
        .thenReturn(Calls.response(Collections.emptyList()));

    // When
    List<Manifest> result =
        CloudResourcesExpressionFunctionProvider.getK8sManifestsByKindsAndLabels(
            "Service", "test-account", "test-namespace", labels);

    // Then
    assertTrue(result.isEmpty());
    verify(oortService)
        .getManifestsByKindsAndLabels("test-account", "test-namespace", "Service", labels);
  }

  @Test
  void getK8sManifestsByKindsAndLabels_returnsEmptyList_whenOortServiceThrowsException() {
    // Given
    Map<String, String> labels = createLabels();
    when(oortService.getManifestsByKindsAndLabels(
            "test-account", "test-namespace", "ConfigMap", labels))
        .thenThrow(new RuntimeException("Service error"));

    // When
    List<Manifest> result =
        CloudResourcesExpressionFunctionProvider.getK8sManifestsByKindsAndLabels(
            "ConfigMap", "test-account", "test-namespace", labels);

    // Then
    assertTrue(result.isEmpty());
    verify(oortService)
        .getManifestsByKindsAndLabels("test-account", "test-namespace", "ConfigMap", labels);
  }

  @Test
  void getK8sManifestsByKindsAndLabels_returnsEmptyList_whenOortServiceIsNull() {
    // Given - create a new provider with null oortService
    new CloudResourcesExpressionFunctionProvider(null);
    Map<String, String> labels = createLabels();

    // When
    List<Manifest> result =
        CloudResourcesExpressionFunctionProvider.getK8sManifestsByKindsAndLabels(
            "Pod", "test-account", "test-namespace", labels);

    // Then
    assertTrue(result.isEmpty());
  }

  @Test
  void getK8sManifestsByKindsAndLabels_handlesNullLabels() {
    // Given - Retrofit 2 rejects a null @QueryMap, so null labels are sent as no labels
    List<Manifest> expectedManifests = List.of(createManifest("manifest-1"));
    when(oortService.getManifestsByKindsAndLabels(
            "test-account", "test-namespace", "Secret", Map.of()))
        .thenReturn(Calls.response(expectedManifests));

    // When
    List<Manifest> result =
        CloudResourcesExpressionFunctionProvider.getK8sManifestsByKindsAndLabels(
            "Secret", "test-account", "test-namespace", null);

    // Then
    assertEquals(expectedManifests, result);
    verify(oortService)
        .getManifestsByKindsAndLabels("test-account", "test-namespace", "Secret", Map.of());
  }

  @Test
  void getK8sManifestsByKindsAndLabels_handlesEmptyLabels() {
    // Given
    List<Manifest> expectedManifests = List.of(createManifest("manifest-1"));
    Map<String, String> emptyLabels = Collections.emptyMap();
    when(oortService.getManifestsByKindsAndLabels(
            "test-account", "test-namespace", "Ingress", emptyLabels))
        .thenReturn(Calls.response(expectedManifests));

    // When
    List<Manifest> result =
        CloudResourcesExpressionFunctionProvider.getK8sManifestsByKindsAndLabels(
            "Ingress", "test-account", "test-namespace", emptyLabels);

    // Then
    assertEquals(expectedManifests, result);
    verify(oortService)
        .getManifestsByKindsAndLabels("test-account", "test-namespace", "Ingress", emptyLabels);
  }

  @Test
  void getK8sManifestsByKindsAndLabels_handlesComplexLabels() {
    // Given
    Map<String, String> complexLabels = new HashMap<>();
    complexLabels.put("app", "my-app");
    complexLabels.put("version", "v1.2.3");
    complexLabels.put("environment", "production");
    complexLabels.put("team", "platform");

    List<Manifest> expectedManifests =
        Arrays.asList(createManifest("app-deployment-v1"), createManifest("app-deployment-v2"));

    when(oortService.getManifestsByKindsAndLabels(
            "prod-account", "prod-namespace", "Deployment", complexLabels))
        .thenReturn(Calls.response(expectedManifests));

    // When
    List<Manifest> result =
        CloudResourcesExpressionFunctionProvider.getK8sManifestsByKindsAndLabels(
            "Deployment", "prod-account", "prod-namespace", complexLabels);

    // Then
    assertEquals(expectedManifests, result);
    assertEquals(2, result.size());
    assertEquals("app-deployment-v1", result.get(0).getName());
    assertEquals("app-deployment-v2", result.get(1).getName());
    verify(oortService)
        .getManifestsByKindsAndLabels(
            "prod-account", "prod-namespace", "Deployment", complexLabels);
  }

  /**
   * Runs the function against a real Retrofit client and a stubbed clouddriver, to check the
   * request matches the clouddriver endpoint and how its responses are handled.
   */
  @Nested
  class AgainstClouddriver {
    // Retrofit2EncodeCorrectionInterceptor (also used by the production clouddriver client) fully
    // encodes path segments, so the comma between kinds is sent as %2C; clouddriver decodes the
    // path variable before splitting it.
    private static final String PATH =
        "/manifests/test-account/test-namespace/service%2Cdeployment/labels";

    private final WireMockServer clouddriver =
        new WireMockServer(WireMockConfiguration.options().dynamicPort());

    @BeforeEach
    void startClouddriver() {
      clouddriver.start();
      Retrofit retrofit =
          new Retrofit.Builder()
              .baseUrl(clouddriver.baseUrl())
              .client(
                  new OkHttpClient.Builder()
                      .addInterceptor(new Retrofit2EncodeCorrectionInterceptor())
                      .build())
              .addConverterFactory(JacksonConverterFactory.create(OrcaObjectMapper.newInstance()))
              .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
              .build();
      new CloudResourcesExpressionFunctionProvider(retrofit.create(OortService.class));
    }

    @AfterEach
    void stopClouddriver() {
      clouddriver.stop();
    }

    @Test
    void sendsKindsAsOnePathSegmentAndLabelsAsQueryParameters() {
      clouddriver.stubFor(
          WireMock.get(WireMock.urlPathEqualTo(PATH))
              .withQueryParam("app", WireMock.equalTo("my-app"))
              .willReturn(
                  WireMock.okJson(
                      "[{\"account\":\"test-account\",\"name\":\"service my-service\","
                          + "\"location\":\"test-namespace\",\"moniker\":{\"app\":\"my-app\"},"
                          + "\"manifest\":{\"kind\":\"Service\"},\"status\":{},"
                          + "\"artifacts\":[],\"events\":[],\"warnings\":[],\"metrics\":[]},"
                          + "{\"account\":\"test-account\",\"name\":\"deployment my-deployment\","
                          + "\"location\":\"test-namespace\",\"manifest\":{\"kind\":\"Deployment\"}}]")));

      List<Manifest> result =
          CloudResourcesExpressionFunctionProvider.getK8sManifestsByKindsAndLabels(
              "service,deployment", "test-account", "test-namespace", Map.of("app", "my-app"));

      assertEquals(
          List.of("service my-service", "deployment my-deployment"),
          result.stream().map(Manifest::getName).toList());
      assertEquals("Service", result.get(0).getManifest().get("kind"));
      clouddriver.verify(
          1,
          WireMock.getRequestedFor(WireMock.urlPathEqualTo(PATH))
              .withQueryParam("app", WireMock.equalTo("my-app")));
    }

    @Test
    void sendsNoQueryParametersForNullLabels() {
      clouddriver.stubFor(
          WireMock.get(WireMock.urlEqualTo(PATH))
              .willReturn(WireMock.okJson("[{\"name\":\"service my-service\"}]")));

      List<Manifest> result =
          CloudResourcesExpressionFunctionProvider.getK8sManifestsByKindsAndLabels(
              "service,deployment", "test-account", "test-namespace", null);

      assertEquals(1, result.size());
      clouddriver.verify(1, WireMock.getRequestedFor(WireMock.urlEqualTo(PATH)));
    }

    @Test
    void returnsEmptyListWhenClouddriverFindsNoManifests() {
      clouddriver.stubFor(
          WireMock.get(WireMock.urlPathEqualTo(PATH))
              .willReturn(WireMock.aResponse().withStatus(404)));

      List<Manifest> result =
          CloudResourcesExpressionFunctionProvider.getK8sManifestsByKindsAndLabels(
              "service,deployment", "test-account", "test-namespace", Map.of("app", "my-app"));

      assertTrue(result.isEmpty());
    }
  }

  private Manifest createManifest(String name) {
    return Manifest.builder().name(name).build();
  }

  private Map<String, String> createLabels() {
    Map<String, String> labels = new HashMap<>();
    labels.put("app", "test-app");
    return labels;
  }
}
