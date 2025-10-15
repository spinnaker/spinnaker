/*
 * Copyright 2025 Harness, Inc.
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

package com.netflix.spinnaker.orca.pipelinetemplate.tasks.v2;

import static org.junit.jupiter.api.Assertions.*;

import com.netflix.spinnaker.orca.pipelinetemplate.v2schema.model.V2PipelineTemplate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class SaveV2PipelineTemplateTaskTest {

  private final SaveV2PipelineTemplateTask task =
      new SaveV2PipelineTemplateTask() {}; // Anonymous implementation

  @Test
  void shouldRejectTemplateIdWithDots() {
    V2PipelineTemplate template = createMinimalValidTemplate();
    template.setId("invalid.id.with.dots");

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> task.validate(template));
    assertEquals("Pipeline Template IDs cannot have dots", exception.getMessage());
  }

  @Test
  void shouldRejectTemplateWithMissingMetadataName() {
    V2PipelineTemplate template = createMinimalValidTemplate();
    template.getMetadata().setName(null);

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> task.validate(template));
    assertTrue(exception.getMessage().contains("metadata.name"));
  }

  @Test
  void shouldRejectTemplateWithMissingMetadataDescription() {
    V2PipelineTemplate template = createMinimalValidTemplate();
    template.getMetadata().setDescription(null);

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> task.validate(template));
    assertTrue(exception.getMessage().contains("metadata.description"));
  }

  @Test
  void shouldRejectTemplateWithMissingMetadataScopes() {
    V2PipelineTemplate template = createMinimalValidTemplate();
    template.getMetadata().setScopes(null);

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> task.validate(template));
    assertTrue(exception.getMessage().contains("metadata.scopes"));
  }

  @Test
  void shouldRejectTemplateWithEmptyMetadataScopes() {
    V2PipelineTemplate template = createMinimalValidTemplate();
    template.getMetadata().setScopes(Collections.emptyList());

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> task.validate(template));
    assertTrue(exception.getMessage().contains("metadata.scopes"));
  }

  @Test
  void shouldRejectTemplateWithMissingSchema() {
    V2PipelineTemplate template = createMinimalValidTemplate();
    template.setSchema(null);

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> task.validate(template));
    assertTrue(exception.getMessage().contains("schema"));
  }

  @Test
  void shouldRejectTemplateWithMultipleMissingFields() {
    V2PipelineTemplate template = createMinimalValidTemplate();
    template.setSchema(null);
    template.getMetadata().setName(null);
    template.getMetadata().setDescription(null);

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> task.validate(template));
    assertTrue(exception.getMessage().contains("metadata.name"));
    assertTrue(exception.getMessage().contains("metadata.description"));
    assertTrue(exception.getMessage().contains("schema"));
  }

  @Test
  void shouldRejectTemplateWithInvalidSchemaVersion() {
    V2PipelineTemplate template = createMinimalValidTemplate();
    template.setSchema("v1");

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> task.validate(template));
    assertEquals("Invalid schema version: v1", exception.getMessage());
  }

  @Test
  void shouldRejectTemplateWithInvalidMetadataName() {
    V2PipelineTemplate template = createMinimalValidTemplate();
    template.getMetadata().setName("Invalid_Name@");

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> task.validate(template));
    assertTrue(exception.getMessage().contains("Illegal template name"));
    assertTrue(
        exception.getMessage().contains(V2PipelineTemplate.Metadata.TEMPLATE_VALID_NAME_REGEX));
  }

  @Test
  void shouldRejectTemplateWithInvalidVariableName() {
    V2PipelineTemplate template = createMinimalValidTemplate();
    List<V2PipelineTemplate.Variable> variables = new ArrayList<>();

    V2PipelineTemplate.Variable validVariable = new V2PipelineTemplate.Variable();
    validVariable.setName("validName");

    V2PipelineTemplate.Variable invalidVariable = new V2PipelineTemplate.Variable();
    invalidVariable.setName("invalid-name");

    variables.add(validVariable);
    variables.add(invalidVariable);
    template.setVariables(variables);

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> task.validate(template));
    assertTrue(exception.getMessage().contains("Illegal variable names"));
    assertTrue(exception.getMessage().contains("invalid-name"));
    assertTrue(
        exception
            .getMessage()
            .contains(V2PipelineTemplate.Variable.TEMPLATE_VALID_VARIABLE_NAME_REGEX));
  }

  @Test
  void shouldRejectTemplateWithMultipleInvalidVariableNames() {
    V2PipelineTemplate template = createMinimalValidTemplate();
    List<V2PipelineTemplate.Variable> variables = new ArrayList<>();

    V2PipelineTemplate.Variable invalidVariable1 = new V2PipelineTemplate.Variable();
    invalidVariable1.setName("invalid-name-1");

    V2PipelineTemplate.Variable invalidVariable2 = new V2PipelineTemplate.Variable();
    invalidVariable2.setName("invalid-name-2");

    variables.add(invalidVariable1);
    variables.add(invalidVariable2);
    template.setVariables(variables);

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> task.validate(template));
    assertTrue(exception.getMessage().contains("invalid-name-1"));
    assertTrue(exception.getMessage().contains("invalid-name-2"));
  }

  @Test
  void shouldAcceptValidTemplate() {
    V2PipelineTemplate template = createMinimalValidTemplate();

    assertDoesNotThrow(() -> task.validate(template));
  }

  @Test
  void shouldAcceptValidTemplateWithValidVariables() {
    V2PipelineTemplate template = createMinimalValidTemplate();
    List<V2PipelineTemplate.Variable> variables = new ArrayList<>();

    V2PipelineTemplate.Variable variable1 = new V2PipelineTemplate.Variable();
    variable1.setName("validName1");

    V2PipelineTemplate.Variable variable2 = new V2PipelineTemplate.Variable();
    variable2.setName("validName2");

    variables.add(variable1);
    variables.add(variable2);
    template.setVariables(variables);

    assertDoesNotThrow(() -> task.validate(template));
  }

  private V2PipelineTemplate createMinimalValidTemplate() {
    V2PipelineTemplate template = new V2PipelineTemplate();
    template.setId("validId");
    template.setSchema(V2PipelineTemplate.V2_SCHEMA_VERSION);

    V2PipelineTemplate.Metadata metadata = new V2PipelineTemplate.Metadata();
    metadata.setName("Valid Template Name");
    metadata.setDescription("This is a valid template description");
    metadata.setScopes(Collections.singletonList("global"));
    template.setMetadata(metadata);

    Map<String, Object> pipeline = new HashMap<>();
    pipeline.put("application", "myapp");
    template.setPipeline(pipeline);

    return template;
  }
}
