package com.netflix.spinnaker.kork.expressions;

import static org.assertj.core.api.Assertions.assertThat;

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
    ExpressionEvaluationSummary summary = new ExpressionEvaluationSummary();
    StandardEvaluationContext evaluationContext =
        new ExpressionsSupport(Trigger.class)
            .buildEvaluationContext(new Pipeline(new Trigger(100)), true);

    String evaluated =
        new ExpressionTransform(parserContext, parser, Function.identity())
            .transformString(
                "group:artifact:${trigger['buildNumber']}", evaluationContext, summary);

    assertThat(evaluated).isEqualTo("group:artifact:100");
    assertThat(summary.getFailureCount()).isEqualTo(0);
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
