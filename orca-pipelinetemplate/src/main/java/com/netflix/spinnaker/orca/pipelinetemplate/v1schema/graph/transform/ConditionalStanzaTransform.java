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
package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.graph.transform;

import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.PipelineTemplateVisitor;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.Conditional;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration;

import java.util.List;

public class ConditionalStanzaTransform implements PipelineTemplateVisitor {

  TemplateConfiguration templateConfiguration;

  public ConditionalStanzaTransform(TemplateConfiguration templateConfiguration) {
    this.templateConfiguration = templateConfiguration;
  }

  // TODO rz - Don't really like that it's implicitly modifying the configuration as well...
  // ConditionalContainer interface? getAllConditionals() -> List<List<T extends Conditional>>?
  @Override
  public void visitPipelineTemplate(PipelineTemplate pipelineTemplate) {
    trimConditionals(pipelineTemplate.getModules());
    trimConditionals(pipelineTemplate.getStages());
    trimConditionals(templateConfiguration.getModules());
    trimConditionals(templateConfiguration.getStages());
  }

  private static <T extends Conditional> void trimConditionals(List<T> list) {
    for (Conditional el : list) {
      if (el.getWhen() == null || (Boolean) el.getWhen()) {
        continue;
      }
      list.remove(el);
    }
  }
}
