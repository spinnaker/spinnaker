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

package com.netflix.spinnaker.orca.pipelinetemplate.v2schema.graph;

import com.netflix.spinnaker.orca.pipelinetemplate.v2schema.V2PipelineTemplateVisitor;
import com.netflix.spinnaker.orca.pipelinetemplate.v2schema.model.V2PipelineTemplate;
import com.netflix.spinnaker.orca.pipelinetemplate.v2schema.model.V2TemplateConfiguration;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class V2PipelineConfigInheritanceTransform implements V2PipelineTemplateVisitor {

  private V2TemplateConfiguration templateConfiguration;

  public V2PipelineConfigInheritanceTransform(V2TemplateConfiguration templateConfiguration) {
    this.templateConfiguration = templateConfiguration;
  }

  @Override
  public void visitPipelineTemplate(V2PipelineTemplate pipelineTemplate) {
    List<String> inherit = templateConfiguration.getInherit();
    Map<String, Object> pipeline = pipelineTemplate.getPipeline();

    if (!inherit.contains("triggers")) {
      pipeline.put("triggers", Collections.emptyList());
    }
    if (!inherit.contains("parameterConfig")) {
      pipeline.put("parameterConfig", Collections.emptyList());
    }
    if (!inherit.contains("expectedArtifacts")) {
      pipeline.put("expectedArtifacts", Collections.emptyList());
    }
    if (!inherit.contains("notifications")) {
      pipeline.put("notifications", Collections.emptyList());
    }
  }
}
