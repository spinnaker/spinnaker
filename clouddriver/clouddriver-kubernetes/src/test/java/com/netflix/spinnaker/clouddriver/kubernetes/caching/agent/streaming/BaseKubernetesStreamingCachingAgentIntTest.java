/*
 * Copyright 2025 Wise, PLC.
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

package com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.streaming;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

import com.github.tomakehurst.wiremock.*;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.common.ClasspathFileSource;
import com.github.tomakehurst.wiremock.core.*;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.cats.mem.InMemoryNamedCacheFactory;
import com.netflix.spinnaker.cats.provider.DefaultProviderRegistry;
import com.netflix.spinnaker.cats.provider.ProviderRegistry;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.KubernetesProvider;
import com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.BaseKubernetesCachingAgentTest;
import io.kubernetes.client.util.ModelMapper;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(5)
public abstract class BaseKubernetesStreamingCachingAgentIntTest
    extends BaseKubernetesCachingAgentTest {

  private static final String BASE_FILES =
      "com/netflix/spinnaker/clouddriver/kubernetes/caching/agent/streaming";

  protected static WireMockServer wireMockServer;
  protected static String baseUrl;

  protected static final KubernetesProvider kubernetesProvider = new KubernetesProvider();
  protected ProviderRegistry providerRegistry;

  @BeforeAll
  static void initKubeapi(@TempDir File tempDir) throws IOException {
    wireMockServer =
        new WireMockServer(
            WireMockConfiguration.wireMockConfig()
                .usingFilesUnderClasspath(BASE_FILES)
                .dynamicPort());
    wireMockServer.start();

    baseUrl = wireMockServer.baseUrl();

    // kubeconfig to wiremock server
    String kubeconfig =
        "apiVersion: v1\n"
            + "kind: Config\n"
            + "clusters:\n"
            + "- name: test\n"
            + "  cluster:\n"
            + "    server: "
            + baseUrl
            + "\n"
            + "contexts:\n"
            + "- name: test\n"
            + "  context:\n"
            + "    cluster: test\n"
            + "current-context: test";

    File kubeconfigFile = new File(tempDir, "kubeconfig");
    Files.write(kubeconfigFile.toPath(), kubeconfig.getBytes());
    BaseKubernetesCachingAgentTest.kubeconfigFile = kubeconfigFile.getAbsolutePath();
  }

  @AfterAll
  static void cleanup() {
    if (wireMockServer != null) {
      wireMockServer.stop();
    }
  }

  @BeforeEach
  void setup() throws NoSuchFieldException, IllegalAccessException {
    wireMockServer.resetAll();

    providerRegistry =
        new DefaultProviderRegistry(
            ImmutableList.of(kubernetesProvider), new InMemoryNamedCacheFactory());

    // reset cache in ModelMapper in order to refresh api models in every test
    // it is tricky, but we don't have a better way to do it because it is a private static field
    Field modelMapperCache = ModelMapper.class.getDeclaredField("nextDiscoveryRefreshTimeMillis");
    modelMapperCache.setAccessible(true);
    modelMapperCache.set(null, 0L);

    // mock kubeapi
    mockKubeapi("/api", "api.json");
    mockKubeapi("/api/v1", "api_v1.json");
    mockKubeapi("/apis", "apis.json");
    mockKubeapi("/apis/apps/v1", "apis_apps_v1.json");

    // default responses for list and watch
    mockKubeapiList("/apis/apps/v1/deployments", "kubeapi/deployments.default.json");
    mockKubeapiWatch("/apis/apps/v1/deployments");
    mockKubeapiList("/apis/apps/v1/replicasets", "kubeapi/replicasets.default.json");
    mockKubeapiWatch("/apis/apps/v1/replicasets");
    mockKubeapiList("/apis/apps/v1/statefulsets", "kubeapi/statefulsets.default.json");
    mockKubeapiWatch("/apis/apps/v1/statefulsets");
    mockKubeapiList("/api/v1/pods", "kubeapi/pods.default.json");
    mockKubeapiWatch("/api/v1/pods");
  }

  private void mockKubeapi(String path, String file) {
    wireMockServer.stubFor(
        get(urlPathEqualTo(path))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBodyFile("kubeapi/" + file)));
  }

  protected void mockKubeapiList(String path, String file) {
    mockKubeapiList(path, null, file);
  }

  protected void mockKubeapiList(String path, String resourceVersion, String file) {
    StringValuePattern resourceVersionMatcher =
        resourceVersion != null ? equalTo(resourceVersion) : or(equalTo("0"), absent());
    wireMockServer.stubFor(
        get(urlPathEqualTo(path))
            .withQueryParam("resourceVersion", resourceVersionMatcher)
            .withQueryParam("watch", or(equalTo("false"), absent()))
            .willReturn(
                aResponse().withHeader("Content-Type", "application/json").withBodyFile(file)));
  }

  protected void mockKubeapiWatch(String path, WatchEvent... events) {
    mockKubeapiWatch(path, null, events);
  }

  protected void mockKubeapiWatch(String path, String resourceVersion, WatchEvent... events) {
    StringValuePattern resourceVersionMatcher =
        resourceVersion != null ? equalTo(resourceVersion) : matching(".+");
    ResponseDefinitionBuilder response =
        ArrayUtils.isNotEmpty(events)
            ? aResponse()
                .withHeader("Content-Type", "application/json;stream=watch")
                .withChunkedDribbleDelay(5, 100)
                .withBody(
                    Arrays.stream(events).map(WatchEvent::getContent).collect(Collectors.joining()))
            : aResponse().withFixedDelay(5_000).withStatus(200).withBody("");
    wireMockServer.stubFor(
        get(urlPathEqualTo(path))
            .withQueryParam("resourceVersion", resourceVersionMatcher)
            .withQueryParam("watch", equalTo("true"))
            .willReturn(response));
  }

  protected static class WatchEvent {
    String type;
    String file;

    private WatchEvent(String type, String file) {
      this.type = type;
      this.file = file;
    }

    static WatchEvent of(String type, String file) {
      return new WatchEvent(type, file);
    }

    static WatchEvent modified(String file) {
      return new WatchEvent("MODIFIED", file);
    }

    static WatchEvent added(String file) {
      return new WatchEvent("ADDED", file);
    }

    static WatchEvent deleted(String file) {
      return new WatchEvent("DELETED", file);
    }

    String getContent() {
      ClasspathFileSource fileSource = new ClasspathFileSource(BASE_FILES + "/__files");
      String obj = fileSource.getTextFileNamed(file).readContentsAsString();
      // delete all new lines because that's how the watch event is sent
      obj = obj.replaceAll("\\n", "");

      return "{\"type\":\"" + type + "\",\"object\":" + obj + "}\n";
    }
  }
}
