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

package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.graph.v2;

import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.graph.v2.transform.V2ConfigStageInjectionTransform;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.graph.v2.transform.V2DefaultVariableAssignmentTransform;
import com.netflix.spinnaker.orca.pipelinetemplate.v2schema.V2PipelineTemplateVisitor;
import com.netflix.spinnaker.orca.pipelinetemplate.v2schema.model.V2PipelineTemplate;
import com.netflix.spinnaker.orca.pipelinetemplate.v2schema.model.V2TemplateConfiguration;

import java.util.ArrayList;
import java.util.List;

public class V2GraphMutator {

  private List<V2PipelineTemplateVisitor> visitors = new ArrayList<>();

  public V2GraphMutator(V2TemplateConfiguration configuration) {
    visitors.add(new V2DefaultVariableAssignmentTransform(configuration));
    visitors.add(new V2ConfigStageInjectionTransform(configuration));
  }

  public void mutate(V2PipelineTemplate template) {
    visitors.forEach(template::accept);
  }
}
