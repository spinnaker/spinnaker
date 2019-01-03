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

package com.netflix.kayenta.canaryanalysis.orca.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static com.netflix.kayenta.canaryanalysis.orca.stage.RunCanaryStage.STAGE_TYPE;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(DataProviderRunner.class)
public class GenerateCanaryAnalysisResultTaskTest {

  private GenerateCanaryAnalysisResultTask task;

  @DataProvider
  public static Object[][] dataProviderGetAggregatedJudgment() {
    return new Object[][] {
        //final score, marginal, pass, expected didPass result
        { 95D, null, null, true },
        { 60D, 75D,  95D,  false },
        { 85D, 75D,  null, true },
        { 85D, 75D,  95D,  false },
        { 97D, 75D,  95D,  true },
    };
  }

  @Before
  public void before() {
    task = new GenerateCanaryAnalysisResultTask(new ObjectMapper());
  }

  @Test
  public void test_that_getRunCanaryStages_returns_the_expected_sorted_list_of_stages_sorted_by_the_number_in_the_stage_name() {
    Stage stage = mock(Stage.class);
    Execution execution = mock(Execution.class);
    when(stage.getExecution()).thenReturn(execution);
    when(execution.getStages()).thenReturn(ImmutableList.of(
        new Stage(null, STAGE_TYPE, "foo #1", Maps.newHashMap(ImmutableMap.of("index", "0"))),
        new Stage(null, STAGE_TYPE, "foo #3", Maps.newHashMap(ImmutableMap.of("index", "2"))),
        new Stage(null, STAGE_TYPE, "foo #2", Maps.newHashMap(ImmutableMap.of("index", "1"))),
        new Stage(null, STAGE_TYPE, "foo #4", Maps.newHashMap(ImmutableMap.of("index", "3")))
    ));
    List<Stage> actual = task.getRunCanaryStages(stage);
    for (int i = 0; i < 4; i++) {
      assertEquals(String.valueOf(i), actual.get(i).getContext().get("index"));
    }
  }

  @Test
  @UseDataProvider("dataProviderGetAggregatedJudgment")
  public void test_getAggregatedJudgment(Double finalCanaryScore, Double marginalThreshold, Double passThreshold, boolean expected) {
    GenerateCanaryAnalysisResultTask.AggregatedJudgement aggregatedJudgement =
        task.getAggregatedJudgment(finalCanaryScore, marginalThreshold, passThreshold);

    assertEquals(expected, aggregatedJudgement.isDidPassThresholds());
  }
}
