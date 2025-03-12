/*
 * Copyright 2022 Armory, LLC
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.StageResolver;
import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.api.test.OrcaFixture;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaTrafficUpdateInput;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@AutoConfigureMockMvc
public class LambdaTrafficRoutingStageTest extends OrcaFixture {

  @Autowired StageResolver stageResolver;

  @Autowired MockMvc mockMvc;

  @Autowired ObjectMapper mapper;

  @Test
  public void resolveToCorrectTypeTest() {
    StageDefinitionBuilder stageDefinitionBuilder =
        this.stageResolver.getStageDefinitionBuilder(
            LambdaTrafficRoutingStage.class.getSimpleName(), "Aws.LambdaTrafficRoutingStage");

    assertTrue(
        stageDefinitionBuilder.aliases().contains("Aws.LambdaTrafficRoutingStage"),
        "Expected stageDefinitionBuilder to contain Aws.LambdaTrafficRoutingStage");
    assertEquals(
        stageDefinitionBuilder.getType(),
        "lambdaTrafficRouting",
        "Expected stageDefinitionBuilder to be of type lambdaTrafficRouting");
  }

  @Test
  public void LambdaTrafficRoutingStageIntegrationTest() throws Exception {
    String content =
        mapper.writeValueAsString(
            Map.of(
                "application",
                "lambda",
                "stages",
                List.of(
                    Map.of(
                        "refId", "1",
                        "type", "Aws.LambdaTrafficRoutingStage",
                        "functionName", "lambda-myLambda",
                        "region", "us-west-2",
                        "deploymentStrategy", "$BLUEGREEN",
                        "timeout", 30,
                        "aliasName", "alias1",
                        "account", "aws-account"))));
    final MvcResult postResults =
        this.mockMvc
            .perform(
                MockMvcRequestBuilders.post("/orchestrate")
                    .content(content)
                    .contentType(MediaType.APPLICATION_JSON))
            .andReturn();
    MockHttpServletResponse response = postResults.getResponse();
    assertEquals(response.getStatus(), 200);

    Map map = mapper.readValue(response.getContentAsString(), Map.class);
    final MvcResult getResults =
        mockMvc.perform(MockMvcRequestBuilders.get((String) map.get("ref"))).andReturn();

    Execution execution =
        mapper.readValue(getResults.getResponse().getContentAsString(), Execution.class);
    LambdaTrafficUpdateInput context = execution.getStages().get(0).getContext();
    assertEquals("lambda-myLambda", context.getFunctionName());
    assertEquals("us-west-2", context.getRegion());
    assertEquals("$BLUEGREEN", context.getDeploymentStrategy());
    assertEquals(30, context.getTimeout());
    assertEquals("alias1", context.getAliasName());
    assertEquals("aws-account", context.getAccount());
  }
}

@Data
class Execution {
  String status;
  List<Stage> stages;
}

@Data
class Stage {
  String status;
  LambdaTrafficUpdateInput context;
  String type;
}
