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

package com.netflix.spinnaker.orca.kato.pipeline.strategy;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerRetrofitErrorHandler;
import com.netflix.spinnaker.okhttp.SpinnakerRequestInterceptor;
import com.netflix.spinnaker.orca.clouddriver.CloudDriverService;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.model.Cluster;
import com.netflix.spinnaker.orca.clouddriver.model.ServerGroup;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupResolver;
import com.netflix.spinnaker.orca.kato.pipeline.support.SourceResolver;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.HttpStatus;
import retrofit.RestAdapter;
import retrofit.client.OkClient;
import retrofit.converter.JacksonConverter;

public class DetermineSourceServerGroupTaskTest {

  private DetermineSourceServerGroupTask determineSourceServerGroupTask;
  private SourceResolver sourceResolver;
  private TargetServerGroupResolver resolver;

  private static ObjectMapper objectMapper = new ObjectMapper();

  private static OortService oortService;

  private CloudDriverService cloudDriverService;

  @RegisterExtension
  private static WireMockExtension wireMock =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private static void simulateGETApiCall(String url, String body, HttpStatus httpStatus) {
    wireMock.givenThat(
        WireMock.get(urlPathEqualTo(url))
            .willReturn(
                aResponse()
                    .withHeaders(HttpHeaders.noHeaders())
                    .withStatus(httpStatus.value())
                    .withBody(body)));
  }

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

    resolver = new TargetServerGroupResolver();
    resolver.setMapper(objectMapper);
    resolver.setOortService(oortService);
    resolver.setRetrySupport(new RetrySupport());

    cloudDriverService = new CloudDriverService(oortService, new ObjectMapper());
    sourceResolver = new SourceResolver();
    sourceResolver.setMapper(objectMapper);
    sourceResolver.setResolver(resolver);
    sourceResolver.setCloudDriverService(cloudDriverService);

    determineSourceServerGroupTask = new DetermineSourceServerGroupTask();
    determineSourceServerGroupTask.setSourceResolver(sourceResolver);
  }

  @Test
  public void testForbiddenError() throws JsonProcessingException {

    StageExecutionImpl stage = createStage();

    ServerGroup serverGroup = createServerGroup();

    Cluster cluster = createCluster(serverGroup);

    // simulate getCluster() API call in SourceResolver
    simulateGETApiCall(
        "/applications/foo/clusters/test/foo/aws",
        objectMapper.writeValueAsString(cluster),
        HttpStatus.OK);

    // simulate getTargetServerGroup API call in TargetServerGroupResolver
    simulateGETApiCall(
        "/applications/testCluster/clusters/test/testCluster/aws/us-east-1/serverGroups/target/ancestor_asg_dynamic",
        objectMapper.writeValueAsString(serverGroup),
        HttpStatus.FORBIDDEN);

    assertThatThrownBy(() -> determineSourceServerGroupTask.execute(stage))
        .isExactlyInstanceOf(SpinnakerHttpException.class)
        .hasMessage(
            "Status: 403, URL: http://localhost:"
                + wireMock.getPort()
                + "/applications/testCluster/clusters/test/testCluster/aws/us-east-1/serverGroups/target/ancestor_asg_dynamic, Message: Forbidden");
  }

  private @NotNull Cluster createCluster(ServerGroup serverGroup) {
    Cluster cluster = new Cluster();
    cluster.setName("testcluster");
    cluster.setServerGroups(new ArrayList<>(List.of(serverGroup)));
    return cluster;
  }

  private @NotNull ServerGroup createServerGroup() {
    ServerGroup.Asg asg = new ServerGroup.Asg();

    asg.setDesiredCapacity(10);
    asg.setMaxSize(10);
    asg.setMinSize(1);

    ServerGroup.Process process = new ServerGroup.Process();
    process.processName = "testProcess";

    asg.setSuspendedProcesses(List.of(process));

    ServerGroup serverGroup = new ServerGroup();
    serverGroup.account = "test";
    serverGroup.name = "test";
    serverGroup.asg = asg;

    ServerGroup.Image image = new ServerGroup.Image();
    image.imageId = "image/image-1234";
    image.name = "k8";
    serverGroup.image = image;
    return serverGroup;
  }

  private @NotNull StageExecutionImpl createStage() {
    StageExecutionImpl stage =
        new StageExecutionImpl(
            PipelineExecutionImpl.newPipeline("orca"),
            "deploy",
            "deploy",
            new HashMap<>(
                Map.of(
                    "account", "test",
                    "application", "foo",
                    "asgName", "foo-test-v000",
                    "availabilityZones", Map.of("region", List.of("us-east-1")))));

    stage.setContext(
        Map.of(
            "target",
            TargetServerGroup.Params.Target.ancestor_asg_dynamic.name(),
            "source",
            Map.of(
                "account",
                "test",
                "region",
                "us-east-1",
                "asgName",
                "foo-test-v000",
                "serverGroupName",
                "test",
                "clusterName",
                "testCluster")));
    return stage;
  }
}
