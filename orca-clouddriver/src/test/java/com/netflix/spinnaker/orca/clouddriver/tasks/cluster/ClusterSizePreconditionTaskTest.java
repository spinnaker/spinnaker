/*
 * Copyright 2024 OpsMx, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.cluster;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerConversionException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerRetrofitErrorHandler;
import com.netflix.spinnaker.okhttp.SpinnakerRequestInterceptor;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.HttpStatus;
import retrofit.RestAdapter;
import retrofit.client.OkClient;
import retrofit.converter.JacksonConverter;

public class ClusterSizePreconditionTaskTest {

  private static ObjectMapper objectMapper = new ObjectMapper();

  private static OortService oortService;

  private ClusterSizePreconditionTask clusterSizePreconditionTask;

  @RegisterExtension
  private static WireMockExtension wireMock =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @BeforeAll
  public static void setupOnce(WireMockRuntimeInfo wmRuntimeInfo) {

    OkClient okClient = new OkClient();
    RestAdapter.LogLevel retrofitLogLevel = RestAdapter.LogLevel.NONE;

    oortService =
        new RestAdapter.Builder()
            .setRequestInterceptor(new SpinnakerRequestInterceptor(true))
            .setEndpoint(wmRuntimeInfo.getHttpBaseUrl())
            .setClient(okClient)
            .setLogLevel(retrofitLogLevel)
            .setErrorHandler(SpinnakerRetrofitErrorHandler.getInstance())
            .setConverter(new JacksonConverter(objectMapper))
            .build()
            .create(OortService.class);
  }

  @BeforeEach
  public void setup() {
    clusterSizePreconditionTask = new ClusterSizePreconditionTask();
    clusterSizePreconditionTask.setOortService(oortService);
    clusterSizePreconditionTask.setObjectMapper(objectMapper);
  }

  private static void simulateFault(String url, String body, HttpStatus httpStatus) {
    wireMock.givenThat(
        WireMock.get(urlPathEqualTo(url))
            .willReturn(
                aResponse()
                    .withHeaders(HttpHeaders.noHeaders())
                    .withStatus(httpStatus.value())
                    .withBody(body)));
  }

  @Test
  public void verifyConversionException() {

    var application = "testapp";

    var stage =
        new StageExecutionImpl(
            PipelineExecutionImpl.newPipeline(application),
            "checkCluster",
            new HashMap<>(
                Map.of(
                    "context",
                    Map.of(
                        "credentials",
                        "test",
                        "cluster",
                        "foo",
                        "regions",
                        List.of(
                            "eu-west-1", "eu-west-1", "us-west-1", "us-east-1", "us-west-2")))));

    // simulate conversion exception by passing invalid json body
    simulateFault("/applications/foo/clusters/test/foo/aws", "{non-json-response}", HttpStatus.OK);

    assertThatThrownBy(() -> clusterSizePreconditionTask.execute(stage))
        .isExactlyInstanceOf(SpinnakerConversionException.class)
        .hasMessage(
            "com.fasterxml.jackson.core.JsonParseException: Unexpected character ('n' (code 110)): was expecting double-quote to start field name\n"
                + " at [Source: (ByteArrayInputStream); line: 1, column: 3]");
  }
}
