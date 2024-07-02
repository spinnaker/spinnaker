package com.netflix.spinnaker.echo.pipelinetriggers.postprocessors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.kork.artifacts.model.ExpectedArtifact;
import com.netflix.spinnaker.kork.expressions.ExpressionEvaluationSummary;
import com.netflix.spinnaker.kork.expressions.ExpressionTransform;
import com.netflix.spinnaker.kork.expressions.config.ExpressionProperties;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParserContext;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.SpelParserConfiguration;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

/**
 * Attempts to evaluate expressions in the pipeline's expected artifacts based on the pipeline's
 * execution context to this point.
 */
@Component
public class ExpectedArtifactExpressionEvaluationPostProcessor implements PipelinePostProcessor {
  private final ObjectMapper mapper;
  private final ExpressionParser parser;
  private final ParserContext parserContext = new TemplateParserContext("${", "}");

  public ExpectedArtifactExpressionEvaluationPostProcessor(
      ObjectMapper mapper, ExpressionProperties expressionProperties) {
    this.mapper = mapper;
    parser =
        new SpelExpressionParser(
            expressionProperties.getMaxExpressionLength() > 0
                ? new SpelParserConfiguration(
                    null, null, false, false, 0, expressionProperties.getMaxExpressionLength())
                : new SpelParserConfiguration());
  }

  @Override
  public Pipeline processPipeline(Pipeline inputPipeline) {
    EvaluationContext evaluationContext = new StandardEvaluationContext(inputPipeline);

    List<ExpectedArtifact> expectedArtifacts = inputPipeline.getExpectedArtifacts();
    if (expectedArtifacts == null) {
      expectedArtifacts = Collections.emptyList();
    }

    return inputPipeline.withExpectedArtifacts(
        expectedArtifacts.stream()
            .map(
                artifact -> {
                  ExpressionEvaluationSummary summary = new ExpressionEvaluationSummary();
                  Map<String, Object> artifactMap =
                      mapper.convertValue(artifact, new TypeReference<Map<String, Object>>() {});

                  Map<String, Object> evaluatedArtifact =
                      new ExpressionTransform(parserContext, parser, Function.identity())
                          .transformMap(artifactMap, evaluationContext, summary);

                  return summary.getTotalEvaluated() > 0
                      ? mapper.convertValue(evaluatedArtifact, ExpectedArtifact.class)
                      : artifact;
                })
            .collect(Collectors.toList()));
  }

  @Override
  public PostProcessorPriority priority() {
    return PostProcessorPriority.EXPECTED_ARTIFACT_EXPRESSION_EVALUATION;
  }
}
