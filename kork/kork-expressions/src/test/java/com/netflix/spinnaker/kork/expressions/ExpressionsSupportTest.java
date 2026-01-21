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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.netflix.spinnaker.kork.artifacts.ArtifactTypes;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactDecorator;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactReferenceURI;
import com.netflix.spinnaker.kork.artifacts.artifactstore.ArtifactStore;
import com.netflix.spinnaker.kork.artifacts.artifactstore.entities.EntityHelper;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.expressions.config.ExpressionProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParserContext;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ExpressionsSupportTest {
  private final ExpressionParser parser = new SpelExpressionParser();
  private final ParserContext parserContext = new TemplateParserContext("${", "}");
  private final MockArtifactStore artifactStore = new MockArtifactStore();

  @BeforeAll
  public void init() {
    ArtifactStore.setInstance(artifactStore);
  }

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

  @ParameterizedTest
  @MethodSource("artifactReferenceArgs")
  public void artifactReferenceInSpEL(String uri, String reference, String expected, String expr) {
    ExpressionProperties expressionProperties = new ExpressionProperties();
    artifactStore.cache.put(uri, reference);
    Map<String, Object> testContext =
        Collections.singletonMap("artifact", Artifact.builder().reference(uri));

    ExpressionsSupport expressionsSupport = new ExpressionsSupport(null, expressionProperties);

    StandardEvaluationContext evaluationContext =
        expressionsSupport.buildEvaluationContext(testContext, true);

    String evaluated =
        new ExpressionTransform(parserContext, parser, Function.identity())
            .transformString(expr, evaluationContext, new ExpressionEvaluationSummary());

    assertThat(evaluated).isEqualTo(expected);
  }

  @SneakyThrows
  public static Stream<Arguments> artifactReferenceArgs() {
    return Stream.of(
        Arguments.of(
            "ref://app/sha1", "Hello world", "Hello world", "${#fromBase64(\"ref://app/sha1\")}"),
        Arguments.of(
            "ref://app/sha2",
            "Hello world",
            Base64.getEncoder().encodeToString("Hello world".getBytes()),
            "${#fetchReference(artifact.reference)}"));
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

  @ParameterizedTest
  @MethodSource("checkMethodFilteringArgs")
  public void checkMethodFiltering(String input, Object expected) {
    System.setProperty("long", "123");
    System.setProperty("int", "456");
    System.setProperty("bool", "true");

    ExpressionProperties expressionProperties = new ExpressionProperties();
    expressionProperties.getDoNotEvalSpel().setEnabled(true);

    Object evaluated =
        new ExpressionTransform(parserContext, parser, Function.identity())
            .transform(
                input,
                new ExpressionsSupport(null, expressionProperties)
                    .buildEvaluationContext(Map.of(), true),
                new ExpressionEvaluationSummary(),
                Map.of());

    assertThat(evaluated).isEqualTo(expected);
  }

  public static Stream<Arguments> checkMethodFilteringArgs() {
    // reject static methods that retrieve from System.Properties as the values
    // there can contain sensitive information
    String invalidLongInput = "${ T(Long).getLong(\"long\") }";
    String invalidIntInput = "${ T(Integer).getInteger(\"int\") }";
    String invalidBoolInput = "${ T(Boolean).getBoolean(\"bool\") }";

    // Ensure other static methods are able to pass
    String validLongInput = "${ T(Long).parseLong(\"123\") }";
    String validIntInput = "${ T(Integer).parseInt(\"456\") }";
    String validBoolInput = "${ T(Boolean).parseBoolean(\"true\") }";

    return Stream.of(
        // Ensure rejected methods return themselves as when a SpEL evaluation
        // fails, Spinnaker returns the same expression.
        Arguments.of(invalidLongInput, invalidLongInput),
        Arguments.of(invalidIntInput, invalidIntInput),
        Arguments.of(invalidBoolInput, invalidBoolInput),
        // Ensure our allowed methods evaluate correctly
        Arguments.of(validLongInput, 123L),
        Arguments.of(validIntInput, 456),
        Arguments.of(validBoolInput, true));
  }

  @Test
  public void testUnmodifiableMapToString() {
    ExpressionProperties expressionProperties = new ExpressionProperties();

    Map<String, Object> testContext = Collections.emptyMap();

    // This fails under java 17 without something like
    // --add-opens=java.base/java.util=ALL-UNNAMED as an argument ot the jvm.
    String testInput = "${ {'foo': 'bar'}.toString() }";

    ExpressionEvaluationSummary summary = new ExpressionEvaluationSummary();
    String evaluated =
        new ExpressionTransform(parserContext, parser, Function.identity())
            .transformString(
                testInput,
                new ExpressionsSupport(null, expressionProperties)
                    .buildEvaluationContext(testContext, true),
                summary);

    assertThat(summary.getExpressionResult()).isEmpty();
    assertThat(summary.getFailureCount()).isEqualTo(0);
    assertThat(evaluated).isEqualTo("{foo=bar}");
  }

  @ParameterizedTest
  @MethodSource("manifestsAndSpELArgs")
  public void manifestsAndSpEL(
      String uri, String expr, String reference, Object expected, Map<String, Object> testContext) {
    ExpressionProperties expressionProperties = new ExpressionProperties();
    artifactStore.cache.put(uri, reference);

    ExpressionsSupport expressionsSupport = new ExpressionsSupport(null, expressionProperties);

    StandardEvaluationContext evaluationContext =
        expressionsSupport.buildEvaluationContext(testContext, true);

    Object evaluated =
        new ExpressionTransform(parserContext, parser, Function.identity())
            .transform(expr, evaluationContext, new ExpressionEvaluationSummary(), Map.of());

    assertThat(evaluated).isEqualTo(expected);
  }

  public Stream<Arguments> manifestsAndSpELArgs() {
    return Stream.of(
        Arguments.of(
            "ref://app/manifestsAndSpEL1",
            "${ manifest.name }",
            "{\"name\":\"foo\"}",
            "foo",
            Collections.singletonMap(
                "manifest",
                EntityHelper.toMap(
                    Artifact.builder()
                        .type(ArtifactTypes.REMOTE_MAP_BASE64.getMimeType())
                        .reference("ref://app/manifestsAndSpEL1")
                        .build()))),
        Arguments.of(
            "ref://app/manifestsAndSpEL2",
            "${ manifests[0].name }",
            "{\"name\":\"foo\"}",
            "foo",
            Collections.singletonMap(
                "manifests",
                List.of(
                    EntityHelper.toMap(
                        Artifact.builder()
                            .type(ArtifactTypes.REMOTE_MAP_BASE64.getMimeType())
                            .reference("ref://app/manifestsAndSpEL2")
                            .build())))),
        Arguments.of(
            "ref://app/manifestsAndSpEL3",
            "${ manifest.name }",
            "{\"name\":\"foo\"}",
            "foo",
            Collections.singletonMap(
                "manifest",
                Map.of(
                    "type",
                    ArtifactTypes.REMOTE_MAP_BASE64.getMimeType(),
                    "reference",
                    "ref://app/manifestsAndSpEL3"))),
        Arguments.of(
            "ref://app/manifestsAndSpEL4",
            "${ manifests[0].name }",
            "{\"name\":\"foo\"}",
            "foo",
            Collections.singletonMap(
                "manifests",
                List.of(
                    Map.of(
                        "type",
                        ArtifactTypes.REMOTE_MAP_BASE64.getMimeType(),
                        "reference",
                        "ref://app/manifestsAndSpEL4")))));
  }

  public class MockArtifactStore extends ArtifactStore {
    public Map<String, String> cache = new HashMap<>();

    public MockArtifactStore() {
      super(null, null, new HashMap<>());
    }

    @Override
    public Artifact store(Artifact artifact, ArtifactDecorator... decorators) {
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
