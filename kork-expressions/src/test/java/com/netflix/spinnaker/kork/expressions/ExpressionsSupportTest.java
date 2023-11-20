/*
 * Copyright 2022 Armory, Inc.
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

package com.netflix.spinnaker.kork.expressions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactDecorator;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactReferenceURI;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStore;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.expressions.config.ExpressionProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParserContext;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

public class ExpressionsSupportTest {
  private final ExpressionParser parser = new SpelExpressionParser();
  private final ParserContext parserContext = new TemplateParserContext("${", "}");

  @Test
  public void testToJsonWhenExpression() {
    Map<String, Object> testInput = Collections.singletonMap("owner", "managed-by-${team}");

    assertThrows(
        SpelHelperFunctionException.class,
        () -> ExpressionsSupport.JsonExpressionFunctionProvider.toJson(testInput));
  }

  @Test
  public void testToJsonWhenNotEvaluableExpression() {
    Map<String, Object> testInput = Collections.singletonMap("owner", "managed-by-${team}");
    NotEvaluableExpression notEvaluableExpression =
        ExpressionsSupport.FlowExpressionFunctionProvider.doNotEval(testInput);

    String result =
        ExpressionsSupport.JsonExpressionFunctionProvider.toJson(notEvaluableExpression);

    assertEquals("{\"owner\":\"managed-by-${team}\"}", result);
  }

  @Test
  public void testToJsonWhenExpressionAndEvaluationContext() {
    ExpressionProperties expressionProperties = new ExpressionProperties();
    expressionProperties.getDoNotEvalSpel().setEnabled(true);

    Map<String, Object> testContext =
        Collections.singletonMap(
            "file_json", Collections.singletonMap("owner", "managed-by-${team}"));
    String testInput = "${#toJson(#doNotEval(file_json))}";

    String evaluated =
        new ExpressionTransform(parserContext, parser, Function.identity())
            .transformString(
                testInput,
                new ExpressionsSupport(null, expressionProperties)
                    .buildEvaluationContext(testContext, true),
                new ExpressionEvaluationSummary());

    assertThat(evaluated).isEqualTo("{\"owner\":\"managed-by-${team}\"}");
  }

  @Test
  public void testToJsonWhenComposedExpressionAndEvaluationContext() {
    ExpressionProperties expressionProperties = new ExpressionProperties();
    expressionProperties.getDoNotEvalSpel().setEnabled(true);

    Map<String, Object> testContext =
        Collections.singletonMap(
            "file_json",
            Collections.singletonMap("json_file", "${#toJson(#doNotEval(file_json))}"));
    String testInput = "${#toJson(#doNotEval(file_json))}";

    String evaluated =
        new ExpressionTransform(parserContext, parser, Function.identity())
            .transformString(
                testInput,
                new ExpressionsSupport(null, expressionProperties)
                    .buildEvaluationContext(testContext, true),
                new ExpressionEvaluationSummary());

    assertThat(evaluated).isEqualTo("{\"json_file\":\"${#toJson(#doNotEval(file_json))}\"}");
  }

  @Test
  public void artifactReferenceInSpEL() {
    MockArtifactStore artifactStore = new MockArtifactStore();
    ArtifactStore.setInstance(artifactStore);
    ExpressionProperties expressionProperties = new ExpressionProperties();
    String expectedValue = "Hello world";
    artifactStore.cache.put("ref://app/sha", expectedValue);
    String expr = "${#fromBase64(\"ref://app/sha\")}";
    Map<String, Object> testContext =
        Collections.singletonMap(
            "artifactReference", Collections.singletonMap("artifactReference", expr));

    ExpressionsSupport expressionsSupport = new ExpressionsSupport(null, expressionProperties);

    StandardEvaluationContext evaluationContext =
        expressionsSupport.buildEvaluationContext(
            new ExpressionTransformTest.Pipeline(new ExpressionTransformTest.Trigger(123)), true);

    String evaluated =
        new ExpressionTransform(parserContext, parser, Function.identity())
            .transformString(expr, evaluationContext, new ExpressionEvaluationSummary());

    assertThat(evaluated).isEqualTo(expectedValue);
  }

  @Test
  public void delegatesTypeConversion() {
    // If a thing is not an artifact URI, it should delegate to StandardTypeConverter
    ExpressionProperties expressionProperties = new ExpressionProperties();

    // StandardTypeConverter does things like convert ints to longs
    String testInput = ("${new java.util.UUID(0,0).toString()}");
    Map<String, Object> testContext = Map.of();

    String evaluated =
        new ExpressionTransform(parserContext, parser, Function.identity())
            .transformString(
                testInput,
                new ExpressionsSupport(null, expressionProperties)
                    .buildEvaluationContext(testContext, true),
                new ExpressionEvaluationSummary());

    assertThat(evaluated).isEqualTo("00000000-0000-0000-0000-000000000000");
  }

  public class MockArtifactStore extends ArtifactStore {
    public Map<String, String> cache = new HashMap<>();

    public MockArtifactStore() {
      super(null, null);
    }

    @Override
    public Artifact store(Artifact artifact) {
      return null;
    }

    @Override
    public Artifact get(ArtifactReferenceURI uri, ArtifactDecorator... decorators) {
      String reference = cache.get(uri.uri());
      Artifact.ArtifactBuilder builder =
          Artifact.builder()
              .reference(
                  Base64.getEncoder().encodeToString(reference.getBytes(StandardCharsets.UTF_8)));
      if (decorators != null) {
        for (ArtifactDecorator decorator : decorators) {
          builder = decorator.decorate(builder);
        }
      }

      return builder.build();
    }
  }
}
