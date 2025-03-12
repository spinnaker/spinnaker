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

package com.netflix.spinnaker.echo.pipelinetriggers.postprocessors;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.netflix.spinnaker.echo.jackson.EchoObjectMapper;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact;
import com.netflix.spinnaker.kork.expressions.config.ExpressionProperties;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

@ContextConfiguration(classes = ExpectedArtifactExpressionLengthTest.class)
@SpringBootTest
@EnableConfigurationProperties(ExpressionProperties.class)
@TestPropertySource(properties = {"expression.max-expression-length=11000"})
class ExpectedArtifactExpressionLengthTest {
  @Autowired private ExpressionProperties expressionProperties;

  @Test
  void customExpressionLength() {
    String expression = String.format("%s", repeat("T", 10900));

    ExpectedArtifactExpressionEvaluationPostProcessor artifactPostProcessor =
        new ExpectedArtifactExpressionEvaluationPostProcessor(
            EchoObjectMapper.getInstance(), expressionProperties);

    Trigger trigger =
        Trigger.builder()
            .enabled(true)
            .type("jenkins")
            .master("master")
            .job(expression)
            .buildNumber(100)
            .build();

    ExpectedArtifact artifact =
        ExpectedArtifact.builder()
            .matchArtifact(
                Artifact.builder()
                    .name(
                        "group:artifact:${trigger['job'] == '"
                            + expression
                            + "' ? 'expr-worked' : 'expr-not-worked'}")
                    .version("${trigger['buildNumber']}")
                    .type("maven/file")
                    .build())
            .id("testId")
            .build();

    Pipeline inputPipeline =
        Pipeline.builder()
            .application("application")
            .name("name")
            .id(Integer.toString(new AtomicInteger(1).getAndIncrement()))
            .trigger(trigger)
            .expectedArtifacts(List.of(artifact))
            .build()
            .withTrigger(trigger);

    Pipeline outputPipeline = artifactPostProcessor.processPipeline(inputPipeline);

    Artifact evaluatedArtifact = outputPipeline.getExpectedArtifacts().get(0).getMatchArtifact();

    assertTrue(evaluatedArtifact.getName().equalsIgnoreCase("group:artifact:expr-worked"));
  }

  private String repeat(String str, int count) {
    String res = "";
    for (int i = 0; i < count; i++) {
      res += str;
    }
    return res;
  }
}
