/*
 * Copyright 2024 Salesforce, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * MultiAuthSupport is in gate-core, but is about matching http requests, so use code from gate-web
 * to test it.
 */
@SpringBootTest(classes = Main.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MultiAuthSupportTest extends GateBootAuthIntegrationTest {
  @BeforeEach
  void init(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());
  }

  @Test
  void handleErrorResponse() throws Exception {
    // given
    String executionId = "12345";
    String expression = "arbitrary";

    // mock an arbitrary endpoint to throw an exception
    doThrow(new IllegalArgumentException("foo"))
        .when(pipelineService)
        .evaluateExpressionForExecution(executionId, expression);

    // when
    HttpResponse<String> response =
        callGateWithPath(
            "/pipelines/%s/evaluateExpression?expression=%s".formatted(executionId, expression),
            "GET");

    // then
    verify(pipelineService).evaluateExpressionForExecution(executionId, expression);
    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response).isNotNull();

    // Validate that the response is json.
    assertThat(objectMapper.readTree(response.body())).isNotNull();
  }
}
