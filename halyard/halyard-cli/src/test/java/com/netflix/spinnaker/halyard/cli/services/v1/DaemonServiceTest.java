package com.netflix.spinnaker.halyard.cli.services.v1;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.catchThrowable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.netflix.spinnaker.halyard.core.DaemonResponse;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.kork.retrofit.util.RetrofitUtils;
import okhttp3.OkHttpClient;
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

    wmDaemon.stubFor(
        get(urlEqualTo("/v1/tasks/" + task.getUuid() + "/"))
            .willReturn(
                aResponse().withStatus(200).withBody(objectMapper.writeValueAsString(task))));

    // FIXME: retrofit2 does not support type variables in method return types, fix getTask() method
    // in DaemonService
    Throwable thrown = catchThrowable(() -> daemonService.getTask(task.getUuid()));
    assertThat(thrown)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Method return type must not include a type variable or wildcard: com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask<C, T>");
  }
}
