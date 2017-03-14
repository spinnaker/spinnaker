/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.validator;

import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate;
import com.netflix.spinnaker.orca.pipelinetemplate.validator.Errors;
import com.netflix.spinnaker.orca.pipelinetemplate.validator.Errors.Error;
import com.netflix.spinnaker.orca.pipelinetemplate.validator.SchemaValidator;
import com.netflix.spinnaker.orca.pipelinetemplate.validator.VersionedSchema;

public class V1TemplateSchemaValidator implements SchemaValidator {

  private static final String SUPPORTED_VERSION = "1";

  @Override
  public void validate(VersionedSchema pipelineTemplate, Errors errors) {
    if (!(pipelineTemplate instanceof PipelineTemplate)) {
      throw new IllegalArgumentException("Expected PipelineTemplate");
    }
    PipelineTemplate template = (PipelineTemplate) pipelineTemplate;

    if (!SUPPORTED_VERSION.equals(template.getSchemaVersion())) {
      errors.addError(Error.builder()
        .withMessage("template schema version is unsupported: expected '" + SUPPORTED_VERSION + "', got '" + template.getSchemaVersion() + "'"));
    }

    V1SchemaValidationHelper.validateStageDefinitions(template.getStages(), errors, V1TemplateSchemaValidator::location);

    // TODO rz - validate variable type & defaultValue combinations
  }

  private static String location(String location) {
    return "template:" + location;
  }
}
