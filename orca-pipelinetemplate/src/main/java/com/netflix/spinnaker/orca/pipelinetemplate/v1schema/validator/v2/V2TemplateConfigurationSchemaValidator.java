/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.validator.v2;

import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.validator.V1SchemaValidationHelper;
import com.netflix.spinnaker.orca.pipelinetemplate.v2schema.model.V2StageDefinition;
import com.netflix.spinnaker.orca.pipelinetemplate.v2schema.model.V2TemplateConfiguration;
import com.netflix.spinnaker.orca.pipelinetemplate.validator.Errors;
import com.netflix.spinnaker.orca.pipelinetemplate.validator.Errors.Error;
import com.netflix.spinnaker.orca.pipelinetemplate.validator.SchemaValidator;
import com.netflix.spinnaker.orca.pipelinetemplate.validator.ValidatorContext;
import com.netflix.spinnaker.orca.pipelinetemplate.validator.VersionedSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class V2TemplateConfigurationSchemaValidator implements SchemaValidator<V2TemplateConfigurationSchemaValidator.SchemaValidatorContext> {

  private static final String SUPPORTED_VERSION = "v2";
  private Logger log = LoggerFactory.getLogger(V2TemplateConfigurationSchemaValidator.class);

  @Override
  public void validate(VersionedSchema configuration, Errors errors, SchemaValidatorContext context) {
    if (!(configuration instanceof V2TemplateConfiguration)) {
      throw new IllegalArgumentException("Expected TemplateConfiguration");
    }
    V2TemplateConfiguration config = (V2TemplateConfiguration) configuration;

    if (!SUPPORTED_VERSION.equals(config.getSchemaVersion())) {
      errors.add(new Error().withMessage("config schema version is unsupported: expected '" + SUPPORTED_VERSION + "', got '" + config.getSchemaVersion() + "'"));
    }

    if (config.getApplication() == null) {
      errors.add(new Error()
        .withMessage("Missing 'application' configuration")
        .withLocation(location("application"))
      );
    }

    // TODO(jacobkiefer): V2 stage definition validators.
//    V1SchemaValidationHelper.validateStageDefinitions(config.getStages(), errors, V2TemplateConfigurationSchemaValidator::location);

    config.getStages().forEach(s -> {
      if (shouldRequireDagRules(s, config, context.stageIds)) {
        errors.add(new Error()
          .withMessage("A configuration-defined stage should have either dependsOn or an inject rule defined")
          .withLocation(location(String.format("stages.%s", s.getRefId())))
          .withSeverity(Errors.Severity.WARN));
      }
    });
  }

  private static boolean shouldRequireDagRules(V2StageDefinition s, V2TemplateConfiguration config, List<String> stageIds) {
    return config.getTemplate() != null &&
      !stageIds.contains(s.getRefId()) &&
      (s.getRequisiteStageRefIds() == null || s.getRequisiteStageRefIds().isEmpty()) &&
      (s.getInject() == null || !s.getInject().hasAny());
  }

  private static String location(String location) {
    return "configuration:" + location;
  }

  public static class SchemaValidatorContext implements ValidatorContext {
    List<String> stageIds;

    public SchemaValidatorContext(List<String> stageIds) {
      this.stageIds = stageIds;
    }
  }
}
