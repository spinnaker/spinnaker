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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import retrofit2.Retrofit;

class OortServiceTest {

  private final WireMockServer mockServer = new WireMockServer();
  private OortService oortService;

  @BeforeEach
  void setUp() {
    mockServer.start();
    Retrofit retrofit = new Retrofit.Builder().baseUrl(mockServer.baseUrl()).build();

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

    // FIXME: fix parameter order
    assertThatThrownBy(
            () ->
                oortService.getServerGroupFromCluster(
                    "spinnaker", "myAccount", "myCluster", "myServerGroup", "aws", "us-west-2"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "A @Path parameter must not come after a @Query. (parameter #6)\n    for method OortService.getServerGroupFromCluster");
  }
}
