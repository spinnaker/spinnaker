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

package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.handler.v2;

import com.netflix.spinnaker.orca.pipelinetemplate.handler.Handler;
import com.netflix.spinnaker.orca.pipelinetemplate.handler.HandlerChain;
import com.netflix.spinnaker.orca.pipelinetemplate.handler.PipelineTemplateContext;
import com.netflix.spinnaker.orca.pipelinetemplate.handler.v2.V2PipelineTemplateContext;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.validator.v2.V2TemplateSchemaValidator;
import com.netflix.spinnaker.orca.pipelinetemplate.validator.Errors;

public class V2TemplateValidationHandler implements Handler {

  @Override
  public void handle(HandlerChain chain, PipelineTemplateContext context) {
    Errors errors = new Errors();
    V2PipelineTemplateContext v2Context = context.getSchemaContext();
    boolean stagesNotEmpty = !v2Context.getConfiguration().getStages().isEmpty();

    V2TemplateSchemaValidator<V2TemplateSchemaValidator.SchemaValidatorContext> validator = new V2TemplateSchemaValidator<>();
    validator.validate(v2Context.getTemplate(),
                       errors,
                       new V2TemplateSchemaValidator.SchemaValidatorContext(stagesNotEmpty));
    if (errors.hasErrors(context.getRequest().getPlan())) {
      context.getErrors().addAll(errors);
      chain.clear();
    }
  }
}
