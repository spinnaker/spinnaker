/*
 * Copyright (c) 2018 Nike, inc.
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

package com.netflix.kayenta.standalonecanaryanalysis.orca.task;

import static com.netflix.kayenta.standalonecanaryanalysis.orca.stage.RunCanaryStage.STAGE_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class GenerateCanaryAnalysisResultTaskTest {

  private GenerateCanaryAnalysisResultTask task;

  public static Object[][] dataProviderGetAggregatedJudgment() {
    return new Object[][] {
      // final score, marginal, pass, expected didPass result
      {95D, null, null, true},
      {60D, 75D, 95D, false},
      {85D, 75D, null, true},
      {85D, 75D, 95D, false},
      {97D, 75D, 95D, true},
    };
  }

  @BeforeEach
  public void before() {
    task = new GenerateCanaryAnalysisResultTask(new ObjectMapper());
  }

  @Test
  public void
      test_that_getRunCanaryStages_returns_the_expected_sorted_list_of_stages_sorted_by_the_number_in_the_stage_name() {
    StageExecution stage = mock(StageExecution.class);
    PipelineExecution execution = mock(PipelineExecution.class);
    when(stage.getExecution()).thenReturn(execution);
    when(execution.getStages())
        .thenReturn(
            ImmutableList.of(
                new StageExecutionImpl(
                    null, STAGE_TYPE, "foo #1", Maps.newHashMap(ImmutableMap.of("index", "0"))),
                new StageExecutionImpl(
                    null, STAGE_TYPE, "foo #3", Maps.newHashMap(ImmutableMap.of("index", "2"))),
                new StageExecutionImpl(
                    null, STAGE_TYPE, "foo #2", Maps.newHashMap(ImmutableMap.of("index", "1"))),
                new StageExecutionImpl(
                    null, STAGE_TYPE, "foo #4", Maps.newHashMap(ImmutableMap.of("index", "3")))));
    List<StageExecution> actual = task.getRunCanaryStages(stage);
    for (int i = 0; i < 4; i++) {
      assertEquals(String.valueOf(i), actual.get(i).getContext().get("index"));
    }
  }

  @ParameterizedTest
  @MethodSource("dataProviderGetAggregatedJudgment")
  public void test_getAggregatedJudgment(
      Double finalCanaryScore, Double marginalThreshold, Double passThreshold, boolean expected) {
    GenerateCanaryAnalysisResultTask.AggregatedJudgement aggregatedJudgement =
        task.getAggregatedJudgment(finalCanaryScore, marginalThreshold, passThreshold);

    assertEquals(expected, aggregatedJudgement.isDidPassThresholds());
  }
}
