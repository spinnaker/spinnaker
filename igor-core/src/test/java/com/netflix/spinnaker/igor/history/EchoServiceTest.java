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

package com.netflix.spinnaker.igor.history;

import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.netflix.spinnaker.igor.build.model.GenericBuild;
import com.netflix.spinnaker.igor.build.model.GenericProject;
import com.netflix.spinnaker.igor.history.model.ArtifactoryEvent;
import com.netflix.spinnaker.igor.history.model.GenericBuildContent;
import com.netflix.spinnaker.igor.history.model.GenericBuildEvent;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import retrofit2.Retrofit;

public class EchoServiceTest {

  @RegisterExtension
  static WireMockExtension wmEcho =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  static EchoService echoService;

  @BeforeAll
  public static void setup() {
    echoService =
        new Retrofit.Builder()
            .baseUrl(wmEcho.baseUrl())
            .client(new OkHttpClient())
            .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
            .addConverterFactory(EchoConverterFactory.create())
            .build()
            .create(EchoService.class);
  }

  @Test
  void testPostEvent_with_GenericBuildEvent() {
    wmEcho.stubFor(WireMock.post(urlEqualTo("/")).willReturn(WireMock.aResponse().withStatus(200)));
    GenericBuildEvent event = buildGenericEvent();
    Retrofit2SyncCall.executeCall(echoService.postEvent(event));
    wmEcho.verify(1, WireMock.postRequestedFor(urlEqualTo("/")));
  }

  @Test
  void testPostEvent_with_ArtifactoryEvent() {
    wmEcho.stubFor(WireMock.post(urlEqualTo("/")).willReturn(WireMock.aResponse().withStatus(200)));
    ArtifactoryEvent event = buildArtifactEvent();
    Retrofit2SyncCall.executeCall(echoService.postEvent(event));
    wmEcho.verify(1, WireMock.postRequestedFor(urlEqualTo("/")));
  }

  private static GenericBuildEvent buildGenericEvent() {
    final GenericBuildEvent event = new GenericBuildEvent();
    final GenericBuildContent buildContent = new GenericBuildContent();
    final GenericProject project = new GenericProject("spinnaker", new GenericBuild());
    buildContent.setMaster("IgorHealthCheck");
    buildContent.setProject(project);
    event.setContent(buildContent);
    return event;
  }

  private static ArtifactoryEvent buildArtifactEvent() {
    Artifact expectedArtifact =
        Artifact.builder()
            .type("maven/file")
            .reference("com.example" + ":" + "demo" + ":" + "0.0.1-SNAPSHOT")
            .name("com.example" + ":" + "demo")
            .version("0.0.1-SNAPSHOT")
            .provenance("maven-snapshots")
            .location(
                "http://localhost:8082/service/rest/repository/browse/maven-snapshots/com/example/demo/0.0.1-SNAPSHOT/0.0.1-20190828.022502-3/")
            .build();

    return new ArtifactoryEvent(new ArtifactoryEvent.Content("nexus-snapshots", expectedArtifact));
  }
}
