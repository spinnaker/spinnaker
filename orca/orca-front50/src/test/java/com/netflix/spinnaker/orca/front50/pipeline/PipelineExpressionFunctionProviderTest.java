/*
 * Copyright 2023 Salesforce, Inc.
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
package com.netflix.spinnaker.orca.front50.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.kork.expressions.SpelHelperFunctionException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import java.util.List;
import java.util.Map;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import retrofit2.mock.Calls;

@SpringBootTest(classes = PipelineExpressionFunctionProvider.class)
class PipelineExpressionFunctionProviderTest {

  private static final String APPLICATION = "my-application";

  private static final String PIPELINE_NAME = "my-pipeline";

  private static final String PIPELINE_ID = "my-pipeline-id";

  private static final Map<String, Object> PIPELINE =
      Map.of("application", APPLICATION, "name", PIPELINE_NAME, "id", PIPELINE_ID);

  private static final List<Map<String, Object>> PIPELINES = List.of(PIPELINE);

  @MockBean Front50Service front50Service;

  @Autowired PipelineExpressionFunctionProvider pipelineExpressionFunctionProvider;

  PipelineExecution pipelineExecution = PipelineExecutionImpl.newPipeline(APPLICATION);

  @BeforeEach
  void init(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());
  }

  @Test
  void pipelineIdForPipelineNameThatDoesNotExist() {
    String doesNotExistPipelineName = "does-not-exist-pipeline";
    assertThat(doesNotExistPipelineName).isNotEqualTo(PIPELINE_NAME);
    when(front50Service.getPipeline(
            pipelineExecution.getApplication(), doesNotExistPipelineName, true))
        .thenThrow(makeSpinnakerHttpException(404));

    assertThatThrownBy(
            () ->
                PipelineExpressionFunctionProvider.pipelineId(
                    pipelineExecution, doesNotExistPipelineName))
        .isInstanceOf(SpelHelperFunctionException.class)
        .hasMessage(
            String.format(
                "Pipeline with name '%s' could not be found on application %s",
                doesNotExistPipelineName, pipelineExecution.getApplication()));

    verify(front50Service)
        .getPipeline(pipelineExecution.getApplication(), doesNotExistPipelineName, true);
    verifyNoMoreInteractions(front50Service);
  }

  @Test
  void pipelineIdWith500Error() {
    SpinnakerHttpException front50Error = makeSpinnakerHttpException(500);
    when(front50Service.getPipeline(pipelineExecution.getApplication(), PIPELINE_NAME, true))
        .thenThrow(front50Error);

    assertThatThrownBy(
            () -> PipelineExpressionFunctionProvider.pipelineId(pipelineExecution, PIPELINE_NAME))
        .isInstanceOf(SpelHelperFunctionException.class)
        .hasMessage("Failed to evaluate #pipelineId function")
        .hasCause(front50Error);

    // There are 3 retries.
    verify(front50Service, times(3))
        .getPipeline(pipelineExecution.getApplication(), PIPELINE_NAME, true);
    verifyNoMoreInteractions(front50Service);
  }

  @Test
  void pipelineIdForPipelineNameThatExists() {
    when(front50Service.getPipeline(pipelineExecution.getApplication(), PIPELINE_NAME, true))
        .thenReturn(Calls.response(PIPELINE));
    String pipelineId =
        PipelineExpressionFunctionProvider.pipelineId(pipelineExecution, PIPELINE_NAME);

    assertThat(pipelineId).isEqualTo(PIPELINE_ID);

    verify(front50Service).getPipeline(pipelineExecution.getApplication(), PIPELINE_NAME, true);
    verifyNoMoreInteractions(front50Service);
  }

  @Test
  void pipelineIdOrNullForPipelineNameThatDoesNotExist() {
    String doesNotExistPipelineName = "does-not-exist-pipeline";
    assertThat(doesNotExistPipelineName).isNotEqualTo(PIPELINE_NAME);
    when(front50Service.getPipeline(
            pipelineExecution.getApplication(), doesNotExistPipelineName, true))
        .thenThrow(makeSpinnakerHttpException(404));

    assertThat(
            PipelineExpressionFunctionProvider.pipelineIdOrNull(
                pipelineExecution, doesNotExistPipelineName))
        .isNull();

    verify(front50Service)
        .getPipeline(pipelineExecution.getApplication(), doesNotExistPipelineName, true);
    verifyNoMoreInteractions(front50Service);
  }

  @Test
  void pipelineIdOrNullForPipelineNameThatExists() {
    when(front50Service.getPipeline(pipelineExecution.getApplication(), PIPELINE_NAME, true))
        .thenReturn(Calls.response(PIPELINE));
    String pipelineId =
        PipelineExpressionFunctionProvider.pipelineIdOrNull(pipelineExecution, PIPELINE_NAME);

    assertThat(pipelineId).isEqualTo(PIPELINE_ID);

    verify(front50Service).getPipeline(pipelineExecution.getApplication(), PIPELINE_NAME, true);
    verifyNoMoreInteractions(front50Service);
  }

  @Test
  void pipelineIdInApplicationForPipelineNameThatDoesNotExist() {
    String doesNotExistPipelineName = "does-not-exist-pipeline";
    assertThat(doesNotExistPipelineName).isNotEqualTo(PIPELINE_NAME);
    when(front50Service.getPipeline(
            pipelineExecution.getApplication(), doesNotExistPipelineName, true))
        .thenThrow(makeSpinnakerHttpException(404));

    assertThat(
            PipelineExpressionFunctionProvider.pipelineIdInApplication(
                pipelineExecution, doesNotExistPipelineName, pipelineExecution.getApplication()))
        .isNull();

    verify(front50Service)
        .getPipeline(pipelineExecution.getApplication(), doesNotExistPipelineName, true);
    verifyNoMoreInteractions(front50Service);
  }

  @Test
  void pipelineIdInApplicationForPipelineNameThatExists() {
    when(front50Service.getPipeline(pipelineExecution.getApplication(), PIPELINE_NAME, true))
        .thenReturn(Calls.response(PIPELINE));
    String pipelineId =
        PipelineExpressionFunctionProvider.pipelineIdInApplication(
            pipelineExecution, PIPELINE_NAME, pipelineExecution.getApplication());

    assertThat(pipelineId).isEqualTo(PIPELINE_ID);

    verify(front50Service).getPipeline(pipelineExecution.getApplication(), PIPELINE_NAME, true);
    verifyNoMoreInteractions(front50Service);
  }

  @Test
  void pipelineIdInApplicationForApplicationThatDoesNotExist() {
    String doesNotExistApplication = "does-not-exist-application";
    assertThat(doesNotExistApplication).isNotEqualTo(APPLICATION);

    when(front50Service.getPipeline(doesNotExistApplication, PIPELINE_NAME, true))
        .thenThrow(makeSpinnakerHttpException(404));
    String pipelineId =
        PipelineExpressionFunctionProvider.pipelineIdInApplication(
            pipelineExecution, PIPELINE_NAME, doesNotExistApplication);

    assertThat(pipelineId).isNull();

    verify(front50Service).getPipeline(doesNotExistApplication, PIPELINE_NAME, true);
    verifyNoMoreInteractions(front50Service);
  }

  static SpinnakerHttpException makeSpinnakerHttpException(int status) {
    String url = "https://front50";
    retrofit2.Response<String> retrofit2Response =
        retrofit2.Response.error(
            status,
            ResponseBody.create(
                MediaType.parse("application/json"), "{ \"message\": \"arbitrary message\" }"));

    Retrofit retrofit =
        new Retrofit.Builder()
            .baseUrl(url)
            .addConverterFactory(JacksonConverterFactory.create())
            .build();

    return new SpinnakerHttpException(retrofit2Response, retrofit);
  }
}
