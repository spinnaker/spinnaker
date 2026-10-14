/*
 * Copyright 2026 Spinnaker Contributors
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

package com.netflix.kayenta.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.kayenta.canary.CanaryMetricConfig;
import com.netflix.kayenta.canary.providers.metrics.TestConcreteCanaryMetricSetQueryConfig;
import org.junit.jupiter.api.Test;

public class SynchronousQueryProcessorQueryModeTest {

  private static CanaryMetricConfig metric(TestConcreteCanaryMetricSetQueryConfig query) {
    return CanaryMetricConfig.builder().name("m").query(query).build();
  }

  @Test
  public void templateFieldMeansTemplate() {
    assertThat(
            SynchronousQueryProcessor.queryMode(
                metric(TestConcreteCanaryMetricSetQueryConfig.builder().template("x").build())))
        .isEqualTo("template");
  }

  @Test
  public void templateWinsOverNamedTemplate() {
    assertThat(
            SynchronousQueryProcessor.queryMode(
                metric(
                    TestConcreteCanaryMetricSetQueryConfig.builder()
                        .template("x")
                        .customFilterTemplate("n")
                        .build())))
        .isEqualTo("template");
  }

  @Test
  public void onlyCustomFilterTemplateMeansNamedTemplate() {
    assertThat(
            SynchronousQueryProcessor.queryMode(
                metric(
                    TestConcreteCanaryMetricSetQueryConfig.builder()
                        .customFilterTemplate("n")
                        .build())))
        .isEqualTo("namedTemplate");
  }

  @Test
  public void neitherMeansGuided() {
    assertThat(
            SynchronousQueryProcessor.queryMode(
                metric(TestConcreteCanaryMetricSetQueryConfig.builder().build())))
        .isEqualTo("guided");
    assertThat(SynchronousQueryProcessor.queryMode(null)).isEqualTo("guided");
  }
}
