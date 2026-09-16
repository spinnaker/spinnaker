/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.echo.pipelinetriggers.postprocessors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.netflix.spinnaker.echo.jackson.EchoObjectMapper;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.Trigger;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact;
import com.netflix.spinnaker.kork.expressions.config.ExpressionProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExpectedArtifactExpressionEvaluationPostProcessorTest {

  private final ExpectedArtifactExpressionEvaluationPostProcessor artifactPostProcessor =
      new ExpectedArtifactExpressionEvaluationPostProcessor(
          EchoObjectMapper.getInstance(), new ExpressionProperties());

  private final Trigger trigger =
      Trigger.builder()
          .enabled(true)
          .type("jenkins")
          .master("master")
          .job("job")
          .buildNumber(100)
          .build();

  private Pipeline createPipelineWith(List<ExpectedArtifact> expectedArtifacts, Trigger trigger) {
    return Pipeline.builder()
        .application("application")
        .name("name")
        .id("1")
        .triggers(List.of(trigger))
        .expectedArtifacts(expectedArtifacts)
        .build();
  }

  @Test
  void evaluatesExpressionsInExpectedArtifacts() {
    ExpectedArtifact artifact =
        ExpectedArtifact.builder()
            .matchArtifact(
                Artifact.builder()
                    .name("group:artifact:${trigger['buildNumber']}")
                    .version("${trigger['buildNumber']}")
                    .type("maven/file")
                    .build())
            .id("goodId")
            .build();

    Pipeline inputPipeline = createPipelineWith(List.of(artifact), trigger).withTrigger(trigger);

    Pipeline outputPipeline = artifactPostProcessor.processPipeline(inputPipeline);
    Artifact evaluatedArtifact = outputPipeline.getExpectedArtifacts().get(0).getMatchArtifact();

    assertThat(evaluatedArtifact.getName()).isEqualTo("group:artifact:100");
    assertThat(evaluatedArtifact.getVersion()).isEqualTo("100");
  }

  @Test
  void unevaluableExpressionsAreLeftInPlace() {
    // they may be evaluated later in a stage with more context
    ExpectedArtifact artifact =
        ExpectedArtifact.builder()
            .matchArtifact(
                Artifact.builder()
                    .name("group:artifact:${#stage('deploy')['version']}")
                    .type("maven/file")
                    .build())
            .id("goodId")
            .build();

    Pipeline inputPipeline = createPipelineWith(List.of(artifact), trigger).withTrigger(trigger);

    Pipeline outputPipeline = artifactPostProcessor.processPipeline(inputPipeline);
    Artifact evaluatedArtifact = outputPipeline.getExpectedArtifacts().get(0).getMatchArtifact();

    assertThat(evaluatedArtifact.getName())
        .isEqualTo("group:artifact:${#stage('deploy')['version']}");
  }

  @Test
  void noExceptionIsThrownWhenExpectedArtifactsIsNull() {
    Pipeline inputPipeline = createPipelineWith(null, trigger).withTrigger(trigger);

    assertThatCode(() -> artifactPostProcessor.processPipeline(inputPipeline))
        .doesNotThrowAnyException();
  }

  @Test
  void allowsCallingToStringOnAnUnmodifiableMap() {
    ExpectedArtifact artifact =
        ExpectedArtifact.builder()
            .matchArtifact(
                Artifact.builder()
                    // This fails under java 17 without something like
                    // --add-opens=java.base/java.util=ALL-UNNAMED as an argument to the jvm.
                    .name("${ {\"foo\": \"bar\"}.toString() }")
                    .version("77")
                    .type("maven/file")
                    .build())
            .id("goodId")
            .build();

    Pipeline inputPipeline = createPipelineWith(List.of(artifact), trigger).withTrigger(trigger);

    Pipeline outputPipeline = artifactPostProcessor.processPipeline(inputPipeline);
    Artifact evaluatedArtifact = outputPipeline.getExpectedArtifacts().get(0).getMatchArtifact();

    assertThat(evaluatedArtifact.getName()).isEqualTo("{foo=bar}");
    assertThat(evaluatedArtifact.getVersion()).isEqualTo("77");
  }

  @Test
  void blocksArbitraryJavaObjectsLikeProcessRunnersFromResolving() {
    String expression =
        "${ new java.lang.ProcessBuilder(\"echo\", \"bob\", \">\", \"/tmp/bad-process.txt\").start().toString() }";
    ExpectedArtifact artifact =
        ExpectedArtifact.builder()
            .matchArtifact(
                Artifact.builder().name(expression).version("77").type("maven/file").build())
            .id("goodId")
            .build();

    Pipeline inputPipeline = createPipelineWith(List.of(artifact), trigger).withTrigger(trigger);

    Pipeline outputPipeline = artifactPostProcessor.processPipeline(inputPipeline);
    Artifact evaluatedArtifact = outputPipeline.getExpectedArtifacts().get(0).getMatchArtifact();

    assertThat(evaluatedArtifact.getName()).isEqualTo(expression);
  }
}
