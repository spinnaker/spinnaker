/*
 * Copyright 2025 Apple, Inc.
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
 */

package com.netflix.spinnaker.orca.clouddriver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.catchThrowable;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.okhttp.Retrofit2EncodeCorrectionInterceptor;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

class OortServiceTest {

  private final WireMockServer mockServer = new WireMockServer();
  private OortService oortService;

  @BeforeEach
  void setUp() {
    mockServer.start();
    Retrofit retrofit =
        new Retrofit.Builder()
            .baseUrl(mockServer.baseUrl())
            .client(
                new okhttp3.OkHttpClient.Builder()
                    .addInterceptor(new Retrofit2EncodeCorrectionInterceptor())
                    .build())
            .addConverterFactory(JacksonConverterFactory.create())
            .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
            .build();

    oortService = retrofit.create(OortService.class);
  }

  @AfterEach
  void tearDown() {
    mockServer.stop();
  }

  @Test
  void getServerGroupFromClusterPathMustNotComeAfterQuery() {
    mockServer.stubFor(
        WireMock.get(
                WireMock.urlEqualTo(
                    "/applications/spinnaker/clusters/myAccount/myCluster/aws/serverGroups/myServerGroup?region=us-west-2"))
            .willReturn(WireMock.aResponse().withStatus(HttpStatus.OK.value()).withBody("{}")));

    ResponseBody responseBody =
        Retrofit2SyncCall.execute(
            oortService.getServerGroupFromCluster(
                "spinnaker", "myAccount", "myCluster", "myServerGroup", "aws", "us-west-2"));

    assertThat(responseBody).isNotNull();
  }

  @Test
  void verifyCloudFormationStackApi() {
    String stackId =
        "arn:aws:cloudformation:us-west-2:123456789012:stack/my-stack/50d6f6c0-e4a3-11e4-8f3c-500c217fbb7a";
    mockServer.stubFor(
        WireMock.get(WireMock.urlEqualTo("/aws/cloudFormation/stacks/" + stackId))
            .willReturn(
                WireMock.aResponse()
                    .withStatus(HttpStatus.OK.value())
                    .withBody("{\"message\": \"success\"}")));

    // FIXME: actual url contains encoded characters, but stub above does not match
    Throwable thrown =
        catchThrowable(
            () -> Retrofit2SyncCall.executeCall(oortService.getCloudFormationStack(stackId)));
    assertThat(thrown).isInstanceOf(SpinnakerHttpException.class);
    assertThat(thrown.getMessage())
        .isEqualTo(
            "Status: 404, Method: GET, URL: "
                + mockServer.baseUrl()
                + "/aws/cloudFormation/stacks/arn%3Aaws%3Acloudformation%3Aus-west-2%3A123456789012%3Astack/my-stack/50d6f6c0-e4a3-11e4-8f3c-500c217fbb7a, Message: Not Found");
  }
}
