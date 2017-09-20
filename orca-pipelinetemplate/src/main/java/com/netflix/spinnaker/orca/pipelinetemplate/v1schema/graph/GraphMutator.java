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
package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.graph;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.PipelineTemplateVisitor;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.graph.transform.ConditionalStanzaTransform;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.graph.transform.ConfigModuleReplacementTransform;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.graph.transform.ConfigPartialReplacementTransform;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.graph.transform.ConfigStageInjectionTransform;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.graph.transform.DefaultVariableAssignmentTransform;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.graph.transform.PipelineConfigInheritanceTransform;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.graph.transform.RenderTransform;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.graph.transform.StageInheritanceControlTransform;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.graph.transform.TrimConditionalsTransform;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.Renderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GraphMutator {

  List<PipelineTemplateVisitor> visitors = new ArrayList<>();

  public GraphMutator(TemplateConfiguration configuration, Renderer renderer, Registry registry, Map<String, Object> trigger) {
    visitors.add(new DefaultVariableAssignmentTransform(configuration));
    visitors.add(new ConfigModuleReplacementTransform(configuration));
    visitors.add(new ConfigPartialReplacementTransform(configuration));
    visitors.add(new PipelineConfigInheritanceTransform(configuration));
    visitors.add(new RenderTransform(configuration, renderer, registry, trigger));
    visitors.add(new ConfigStageInjectionTransform(configuration));
    visitors.add(new StageInheritanceControlTransform());
    visitors.add(new ConditionalStanzaTransform(configuration, trigger));
    visitors.add(new TrimConditionalsTransform());
  }

  public void mutate(PipelineTemplate template) {
    visitors.forEach(template::accept);
  }

}
