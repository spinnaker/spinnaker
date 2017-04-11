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

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.IllegalTemplateConfigurationException;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.MapMerge;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.PipelineTemplateVisitor;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.StageDefinition.InheritanceControl.Rule;

import java.util.Collection;
import java.util.Map;

public class StageInheritanceControlTransform implements PipelineTemplateVisitor {

  @Override
  public void visitPipelineTemplate(PipelineTemplate pipelineTemplate) {
    pipelineTemplate.getStages()
      .stream()
      .filter(s -> s.getInheritanceControl() != null)
      .forEach(s -> {
        DocumentContext dc = JsonPath.parse(s.getConfig());
        s.getInheritanceControl().getMerge().forEach(r -> merge(dc, r));
        s.getInheritanceControl().getReplace().forEach(r -> replace(dc, r));
        s.getInheritanceControl().getRemove().forEach(r -> dc.delete(r.getPath()));
      });
  }

  @SuppressWarnings("unchecked")
  private static void merge(DocumentContext dc, Rule rule) {
    Object oldValue = dc.read(rule.getPath());
    if (oldValue instanceof Collection) {
      if (!(rule.getValue() instanceof Collection)) {
        throw new IllegalTemplateConfigurationException("cannot merge non-collection value into collection");
      }
      ((Collection) oldValue).addAll((Collection) rule.getValue());
      dc.set(rule.getPath(), oldValue);
    } else if (oldValue instanceof Map) {
      if (!(rule.getValue() instanceof Map)) {
        throw new IllegalTemplateConfigurationException("cannot merge non-map value into map");
      }
      Map<String, Object> merged = MapMerge.merge((Map<String, Object>) oldValue, (Map<String, Object>) rule.getValue());
      dc.set(rule.getPath(), merged);
    } else {
      throw new IllegalTemplateConfigurationException("merge inheritance control must be given a list or map value: " + rule.getPath());
    }
  }

  private static void replace(DocumentContext dc, Rule rule) {
    Object oldValue = dc.read(rule.getPath());
    if (oldValue instanceof Collection && !(rule.getValue() instanceof Collection)) {
      throw new IllegalTemplateConfigurationException("cannot replace collection value with non-collection value");
    } else if (oldValue instanceof Map && !(rule.getValue() instanceof Map)) {
      throw new IllegalTemplateConfigurationException("cannot replace map value with non-map value");
    }
    dc.set(rule.getPath(), rule.getValue());
  }
}
