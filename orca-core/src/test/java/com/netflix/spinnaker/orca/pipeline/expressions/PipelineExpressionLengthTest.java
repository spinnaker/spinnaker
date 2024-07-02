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

package com.netflix.spinnaker.orca.pipeline.expressions;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.netflix.spinnaker.kork.expressions.ExpressionEvaluationSummary;
import com.netflix.spinnaker.kork.expressions.config.ExpressionProperties;
import com.netflix.spinnaker.orca.test.YamlFileApplicationContextInitializer;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.pf4j.PluginManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(
    classes = PipelineExpressionLengthTest.class,
    initializers = PipelineExpressionLengthTest.class)
@SpringBootTest
@EnableConfigurationProperties(ExpressionProperties.class)
public class PipelineExpressionLengthTest extends YamlFileApplicationContextInitializer {

  @Mock private PluginManager pluginManager;
  @Autowired private ExpressionProperties expressionProperties;

  @Override
  protected String getResourceLocation() {
    return "classpath:expression-properties.yml";
  }

  @Test
  void customExpressionLength() {
    String expression = String.format("%s", repeat("T", 10975));
    String rootObjectExpression = String.format("${status.toString() == \"%s\"}", expression);

    Map<String, String> rootObject = Map.of("status", expression);
    Map<String, Object> source = Map.of("test", rootObjectExpression);

    PipelineExpressionEvaluator evaluator =
        new PipelineExpressionEvaluator(new ArrayList<>(), pluginManager, expressionProperties);

    Map<String, Object> result =
        evaluator.evaluate(source, rootObject, new ExpressionEvaluationSummary(), true);
    assertTrue(Boolean.parseBoolean(result.get("test").toString()));
  }

  private String repeat(String str, int count) {
    String res = "";
    for (int i = 0; i < count; i++) {
      res += str;
    }
    return res;
  }
}
