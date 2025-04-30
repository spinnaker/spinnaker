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
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.catchThrowable;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.netflix.spinnaker.igor.build.model.GenericBuild;
import com.netflix.spinnaker.igor.build.model.GenericProject;
import com.netflix.spinnaker.igor.history.model.GenericBuildContent;
import com.netflix.spinnaker.igor.history.model.GenericBuildEvent;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public class EchoServiceTest {

  @RegisterExtension
  static WireMockExtension wmEcho =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  static EchoService echoService;

  @BeforeAll
  public static void setup() {
    wmEcho.stubFor(WireMock.post(urlEqualTo("/")).willReturn(WireMock.aResponse().withStatus(200)));

    echoService =
        new Retrofit.Builder()
            .baseUrl(wmEcho.baseUrl())
            .client(new OkHttpClient())
            .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
            .addConverterFactory(JacksonConverterFactory.create())
            .build()
            .create(EchoService.class);
  }

  @Test
  void testPostEvent_with_GenericBuildEvent() {
    GenericBuildEvent event = buildGenericEvent();
    // TODO: Fix this issue
    Throwable thrown =
        catchThrowable(() -> Retrofit2SyncCall.executeCall(echoService.postEvent(event)));
    assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    assertThat(thrown)
        .hasMessageContaining(
            "Unable to convert GenericBuildEvent(details={type=build, source=igor}, content=GenericBuildContent(project=GenericProject(name=spinnaker, lastBuild=GenericBuild(building=false, fullDisplayName=null, name=null, number=0, duration=null, timestamp=null, result=null, artifacts=null, testResults=null, url=null, id=null, genericGitRevisions=null, properties=null)), master=IgorHealthCheck, type=null)) to RequestBody (parameter #1)");
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
}
