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
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.validator.V1TemplateSchemaValidator.SchemaValidatorContext;
import com.netflix.spinnaker.orca.pipelinetemplate.validator.Errors;
import com.netflix.spinnaker.orca.pipelinetemplate.validator.Errors.Error;
import com.netflix.spinnaker.orca.pipelinetemplate.validator.SchemaValidator;
import com.netflix.spinnaker.orca.pipelinetemplate.validator.ValidatorContext;
import com.netflix.spinnaker.orca.pipelinetemplate.validator.VersionedSchema;

public class V1TemplateSchemaValidator<T extends SchemaValidatorContext> implements SchemaValidator<T> {

  private static final String SUPPORTED_VERSION = "1";

  @Override
  public void validate(VersionedSchema pipelineTemplate, Errors errors, SchemaValidatorContext context) {
    if (!(pipelineTemplate instanceof PipelineTemplate)) {
      throw new IllegalArgumentException("Expected PipelineTemplate");
    }
    PipelineTemplate template = (PipelineTemplate) pipelineTemplate;

    if (!SUPPORTED_VERSION.equals(template.getSchemaVersion())) {
      errors.add(new Error()
        .withMessage("template schema version is unsupported: expected '" + SUPPORTED_VERSION + "', got '" + template.getSchemaVersion() + "'"));
    }

    if (template.getProtect() && context.configHasStages) {
      errors.add(new Error()
        .withMessage("Modification of the stage graph (adding, removing, editing) is disallowed")
        .withCause("The template being used has marked itself as protected")
      );
    }

    V1SchemaValidationHelper.validateStageDefinitions(template.getStages(), errors, V1TemplateSchemaValidator::location);
  }

  private static String location(String location) {
    return "template:" + location;
  }

  public static class SchemaValidatorContext implements ValidatorContext {
    boolean configHasStages = false;

    public SchemaValidatorContext(boolean configHasStages) {
      this.configHasStages = configHasStages;
    }
  }
}
