/*
 * Copyright 2025 OpsMx, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 *
 */

package com.netflix.spinnaker.halyard.cli.services.v1;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.netflix.spinnaker.halyard.core.DaemonResponse;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.kork.retrofit.util.RetrofitUtils;
import java.io.IOException;
import java.util.Comparator;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public class DaemonServiceTest {
  @RegisterExtension
  private static final WireMockExtension wmDaemon =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static DaemonService daemonService;

  @BeforeAll
  public static void setUp() {
    daemonService =
        new Retrofit.Builder()
            .baseUrl(RetrofitUtils.getBaseUrl(wmDaemon.baseUrl()))
            .client(new OkHttpClient())
            .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
            .addConverterFactory(JacksonConverterFactory.create())
            .build()
            .create(DaemonService.class);
  }

  @Test
  public void testGetTask() throws JsonProcessingException {
    DaemonTask task = new DaemonTask<>("testTask", 1000);
    Problem problem = new ProblemBuilder(Problem.Severity.FATAL, "some exception").build();
    DaemonResponse response = new DaemonResponse<>(null, new ProblemSet(problem));
    task.setResponse(response);
    Comparator<Problem> compareBySeverityAndMessage =
        Comparator.comparing(Problem::getSeverity).thenComparing(Problem::getMessage);

    wmDaemon.stubFor(
        get(urlEqualTo("/v1/tasks/" + task.getUuid() + "/"))
            .willReturn(
                aResponse().withStatus(200).withBody(objectMapper.writeValueAsString(task))));
    ResponseBody body = daemonService.getTask(task.getUuid());
    DaemonTask retrievedTask = getTask(body);
    assertThat(retrievedTask.getUuid()).isEqualTo(task.getUuid());
    assertThat(retrievedTask.getResponse().getProblemSet().getProblems()).hasSize(1);
    assertThat(retrievedTask.getResponse().getProblemSet().getProblems().get(0))
        .usingComparator(compareBySeverityAndMessage)
        .isEqualTo(problem);
  }

  private <C, T> DaemonTask<C, T> getTask(ResponseBody body) {
    try {
      String jsonString = body.string();
      System.out.println("Response JSON: " + jsonString);
      return objectMapper.readValue(jsonString, new TypeReference<DaemonTask<C, T>>() {});
    } catch (IOException e) {
      throw new RuntimeException("Failed to parse task response", e);
    }
  }
}
