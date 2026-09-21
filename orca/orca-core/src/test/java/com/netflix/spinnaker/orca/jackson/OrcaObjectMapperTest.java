/*
 * Copyright 2026 Harness, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.jackson;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.Trigger;

import com.netflix.spinnaker.orca.config.JacksonParserProperties;
import com.netflix.spinnaker.orca.pipeline.model.DefaultTrigger;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.databind.ObjectMapper;

class OrcaObjectMapperTest {

  @Test
  void canDeserializeMapWithKeyExceedingDefaultJacksonNameLengthLimit() throws Exception {
    ObjectMapper mapper = OrcaObjectMapper.newInstance();

    String largeKey = "x".repeat(60_000);
    String json = "{\"" + largeKey + "\":\"value\"}";

    Map<String, Object> result = mapper.readValue(json, Map.class);

    assertThat(result).containsKey(largeKey);
    assertThat(result.get(largeKey)).isEqualTo("value");
  }

  @Test
  void defaultStreamReadConstraintsAreRelaxed() {
    ObjectMapper mapper = OrcaObjectMapper.newInstance();
    StreamReadConstraints constraints = mapper.tokenStreamFactory().streamReadConstraints();

    assertThat(constraints.getMaxNameLength()).isEqualTo(200_000);
    assertThat(constraints.getMaxStringLength()).isEqualTo(50_000_000);
    assertThat(constraints.getMaxNestingDepth()).isEqualTo(2_000);
    assertThat(constraints.getMaxNumberLength()).isEqualTo(5_000);
    assertThat(constraints.getMaxDocumentLength()).isEqualTo(-1);
  }

  @Test
  void customPropertiesAreRespected() {
    JacksonParserProperties props = new JacksonParserProperties();
    props.setMaxNameLength(300_000);
    props.setMaxStringLength(100_000_000);
    props.setMaxNestingDepth(3_000);
    props.setMaxNumberLength(10_000);
    props.setMaxDocumentLength(1_000_000_000L);

    ObjectMapper mapper = OrcaObjectMapper.newInstance(props);
    StreamReadConstraints constraints = mapper.tokenStreamFactory().streamReadConstraints();

    assertThat(constraints.getMaxNameLength()).isEqualTo(300_000);
    assertThat(constraints.getMaxStringLength()).isEqualTo(100_000_000);
    assertThat(constraints.getMaxNestingDepth()).isEqualTo(3_000);
    assertThat(constraints.getMaxNumberLength()).isEqualTo(10_000);
    assertThat(constraints.getMaxDocumentLength()).isEqualTo(1_000_000_000L);
  }

  @Test
  void canDeserializeLowercaseExecutionType() throws Exception {
    ObjectMapper mapper = OrcaObjectMapper.newInstance();

    assertThat(mapper.readValue("\"pipeline\"", ExecutionType.class))
        .isEqualTo(ExecutionType.PIPELINE);
  }

  @Test
  void preservesStagesWhenConvertingPipelineExecution() {
    ObjectMapper mapper = OrcaObjectMapper.newInstance();
    PipelineExecution execution =
        new PipelineExecutionImpl(ExecutionType.PIPELINE, "test-application");
    StageExecutionImpl stage = new StageExecutionImpl(execution, "test");
    stage.setId("stage-id");
    execution.getStages().add(stage);

    PipelineExecution converted = mapper.convertValue(execution, PipelineExecution.class);

    assertThat(converted.getStages()).hasSize(1);
    assertThat(converted.getStages().get(0).getId()).isEqualTo("stage-id");
  }

  @Test
  void convertsEmptyTriggerMapToDefaultTrigger() {
    ObjectMapper mapper = OrcaObjectMapper.newInstance();

    Trigger trigger = mapper.convertValue(Map.of(), Trigger.class);

    assertThat(trigger).isInstanceOf(DefaultTrigger.class);
    assertThat(trigger.getType()).isEqualTo("none");
  }

  @Test
  void serializesHttpMethodsAsUppercase() throws Exception {
    ObjectMapper mapper = OrcaObjectMapper.newInstance();

    assertThat(mapper.writeValueAsString(HttpMethod.GET)).isEqualTo("\"GET\"");
  }

  @Test
  void deserializesHttpMethodsCaseInsensitively() throws Exception {
    ObjectMapper mapper = OrcaObjectMapper.newInstance();

    assertThat(mapper.readValue("\"post\"", HttpMethod.class)).isEqualTo(HttpMethod.POST);
  }

  @Test
  void deserializesHttpStatusCodesFromNamesAndNumbers() throws Exception {
    ObjectMapper mapper = OrcaObjectMapper.newInstance();

    assertThat(mapper.readValue("\"OK\"", HttpStatusCode.class)).isEqualTo(HttpStatus.OK);
    assertThat(mapper.readValue("404", HttpStatusCode.class).value()).isEqualTo(404);
    assertThat(mapper.readValue("\"500\"", HttpStatusCode.class).value()).isEqualTo(500);
  }

}
