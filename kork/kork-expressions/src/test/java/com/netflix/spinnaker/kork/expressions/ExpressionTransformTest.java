package com.netflix.spinnaker.kork.expressions;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.kork.expressions.config.ExpressionProperties;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.junit.jupiter.api.Test;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParserContext;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

class ExpressionTransformTest {
  private final ExpressionParser parser = new SpelExpressionParser();
  private final ParserContext parserContext = new TemplateParserContext("${", "}");

  @Test
  void evaluateCompositeExpression() {
    ExpressionProperties expressionProperties = new ExpressionProperties();

    ExpressionEvaluationSummary summary = new ExpressionEvaluationSummary();
    StandardEvaluationContext evaluationContext =
        new ExpressionsSupport(Trigger.class, expressionProperties)
            .buildEvaluationContext(new Pipeline(new Trigger(100)), true);

    String evaluated =
        new ExpressionTransform(parserContext, parser, Function.identity())
            .transformString(
                "group:artifact:${trigger['buildNumber']}", evaluationContext, summary);

    assertThat(evaluated).isEqualTo("group:artifact:100");
    assertThat(summary.getFailureCount()).isEqualTo(0);
  }

  @Test
  void resolvesAHyphenatedIdentifierAsALiteralKeyWhenSupportIsEnabled() {
    Map<String, Object> root = new HashMap<>();
    root.put("my-container-name", "nginx");
    StandardEvaluationContext evaluationContext = new StandardEvaluationContext(root);
    ExpressionEvaluationSummary summary = new ExpressionEvaluationSummary();

    String evaluated =
        new ExpressionTransform(parserContext, parser, Function.identity(), true)
            .transformString("${my-container-name}", evaluationContext, summary);

    assertThat(evaluated).isEqualTo("nginx");
    assertThat(summary.getFailureCount()).isEqualTo(0);
  }

  @Test
  void leavesAHyphenatedIdentifierUnresolvedWhenSupportIsDisabled() {
    Map<String, Object> root = new HashMap<>();
    root.put("my-container-name", "nginx");
    StandardEvaluationContext evaluationContext = new StandardEvaluationContext(root);
    ExpressionEvaluationSummary summary = new ExpressionEvaluationSummary();

    String evaluated =
        new ExpressionTransform(parserContext, parser, Function.identity())
            .transformString("${my-container-name}", evaluationContext, summary);

    assertThat(evaluated).isEqualTo("${my-container-name}");
    assertThat(summary.getFailureCount()).isEqualTo(1);
  }

  @Test
  void stillEvaluatesRealArithmeticSubtractionWhenSupportIsEnabled() {
    StandardEvaluationContext evaluationContext = new StandardEvaluationContext();
    ExpressionEvaluationSummary summary = new ExpressionEvaluationSummary();

    Object evaluated =
        new ExpressionTransform(parserContext, parser, Function.identity(), true)
            .transform("${5 - 2}", evaluationContext, summary, Collections.emptyMap());

    assertThat(evaluated).isEqualTo(3);
    assertThat(summary.getFailureCount()).isEqualTo(0);
  }

  @Test
  void reportsAnUnresolvedHyphenatedKeyAsInfoRatherThanErrorWhenSupportIsEnabled() {
    StandardEvaluationContext evaluationContext = new StandardEvaluationContext(new HashMap<>());
    ExpressionEvaluationSummary summary = new ExpressionEvaluationSummary();

    String evaluated =
        new ExpressionTransform(parserContext, parser, Function.identity(), true)
            .transformString("${no-such-key}", evaluationContext, summary);

    // The rewritten expression is a Map#get lookup, which returns null for a missing key instead
    // of throwing - so this is reported as an unresolved (INFO) expression, same as any other
    // expression that legitimately evaluates to null, rather than as a hard evaluation ERROR.
    assertThat(evaluated).isEqualTo("${no-such-key}");
    assertThat(summary.getFailureCount()).isEqualTo(1);
  }

  @Test
  void evaluateMap() {
    Map<String, Object> input = Collections.singletonMap("key", "value");

    Map<String, Object> evaluated =
        new ExpressionTransform(parserContext, parser, Function.identity())
            .transformMap(
                input, new StandardEvaluationContext(), new ExpressionEvaluationSummary());

    assertThat(evaluated).isEqualTo(input);
  }

  @Test
  void evaluateNestedMap() {
    Map<String, Object> input =
        Collections.singletonMap("key", Collections.singletonMap("inner", "value"));

    Map<String, Object> evaluated =
        new ExpressionTransform(parserContext, parser, Function.identity())
            .transformMap(
                input, new StandardEvaluationContext(), new ExpressionEvaluationSummary());

    assertThat(evaluated).isEqualTo(input);
  }

  @Test
  void evaluateMapWithNestedList() {
    Map<String, Object> input = Collections.singletonMap("key", Collections.singletonList("value"));

    Map<String, Object> evaluated =
        new ExpressionTransform(parserContext, parser, Function.identity())
            .transformMap(
                input, new StandardEvaluationContext(), new ExpressionEvaluationSummary());

    assertThat(evaluated).isEqualTo(input);
  }

  @Test
  void evaluateList() {
    List<Object> input = Collections.singletonList("value");

    List<?> evaluated =
        new ExpressionTransform(parserContext, parser, Function.identity())
            .transformList(
                input,
                new StandardEvaluationContext(),
                new ExpressionEvaluationSummary(),
                Collections.emptyMap());

    assertThat(evaluated).isEqualTo(input);
  }

  @Test
  void evaluateNestedList() {
    ExpressionEvaluationSummary summary = new ExpressionEvaluationSummary();

    List<Object> input = Collections.singletonList(Collections.singletonList("value"));

    List<?> evaluated =
        new ExpressionTransform(parserContext, parser, Function.identity())
            .transformList(input, new StandardEvaluationContext(), summary, Collections.emptyMap());

    assertThat(evaluated).isEqualTo(input);
  }

  @Test
  void evaluateListWithNestedMap() {
    List<Object> input = Collections.singletonList(Collections.singletonMap("key", "value"));

    List<?> evaluated =
        new ExpressionTransform(parserContext, parser, Function.identity())
            .transformList(
                input,
                new StandardEvaluationContext(),
                new ExpressionEvaluationSummary(),
                Collections.emptyMap());

    assertThat(evaluated).isEqualTo(input);
  }

  @AllArgsConstructor
  @Data
  static class Pipeline {
    Trigger trigger;
  }

  @AllArgsConstructor
  @Data
  static class Trigger {
    int buildNumber;
  }
}
