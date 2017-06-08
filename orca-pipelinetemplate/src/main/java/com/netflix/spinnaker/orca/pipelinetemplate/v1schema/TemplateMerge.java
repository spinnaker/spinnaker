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
package com.netflix.spinnaker.orca.pipelinetemplate.v1schema;

import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.Identifiable;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.NamedContent;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate;

import java.util.ArrayList;
import java.util.List;

public class TemplateMerge {

  public static PipelineTemplate merge(List<PipelineTemplate> templates) {
    PipelineTemplate result = new PipelineTemplate();
    result.setId("mergedTemplate");
    result.setSchema(templates.get(0).getSchema());
    for (PipelineTemplate template : templates) {
      appendSource(result, template);

      // If any of the templates are set as protected, configurations won't be allowed to alter stages.
      if (template.getProtect()) {
        result.setProtect(true);
      }

      result.setVariables(mergeNamedContent(result.getVariables(), template.getVariables()));

      mergeConfiguration(result, template);

      result.setStages(mergeIdentifiable(result.getStages(), template.getStages()));
      result.setModules(mergeIdentifiable(result.getModules(), template.getModules()));
      result.setPartials(mergeIdentifiable(result.getPartials(), template.getPartials()));
    }

    // Apply the last template's metadata to the final result
    result.setMetadata(templates.get(templates.size() - 1).getMetadata());

    return result;
  }

  private static void appendSource(PipelineTemplate a, PipelineTemplate b) {
    if (a.getSource() == null || a.getSource().equals("")) {
      a.setSource(b.getSource());
    } else if (b.getSource() != null) {
      a.setSource(a.getSource() + "," + b.getSource());
    }
  }

  private static void mergeConfiguration(PipelineTemplate a, PipelineTemplate b) {
    if (a.getConfiguration() == null) {
      a.setConfiguration(b.getConfiguration());
      return;
    }
    PipelineTemplate.Configuration aConfig = a.getConfiguration();
    PipelineTemplate.Configuration bConfig = b.getConfiguration();

    aConfig.setConcurrentExecutions(MapMerge.merge(aConfig.getConcurrentExecutions(), bConfig.getConcurrentExecutions()));

    aConfig.setTriggers(mergeNamedContent(aConfig.getTriggers(), bConfig.getTriggers()));
    aConfig.setParameters(mergeNamedContent(aConfig.getParameters(), bConfig.getParameters()));
    aConfig.setNotifications(mergeNamedContent(aConfig.getNotifications(), bConfig.getNotifications()));
  }

  public static <T extends NamedContent> List<T> mergeNamedContent(List<T> a, List<T> b) {
    if (a == null || a.size() == 0) {
      return b;
    }

    if (b == null || b.size() == 0) {
      return a;
    }

    List<T> merged = new ArrayList<>();
    merged.addAll(a);
    for (T bNode : b) {
      boolean updated = false;
      for (int i = 0; i < merged.size(); i++) {
        if (merged.get(i).getName().equals(bNode.getName())) {
          merged.set(i, bNode);
          updated = true;
          break;
        }
      }

      if (!updated) {
        merged.add(bNode);
      }
    }

    return merged;
  }

  public static <T extends Identifiable> List<T> mergeIdentifiable(List<T> a, List<T> b) {
    if (a == null || a.size() == 0) {
      return b;
    }

    if (b == null || b.size() == 0) {
      return a;
    }

    List<T> merged = new ArrayList<>();
    merged.addAll(a);
    for (T bNode : b) {
      boolean updated = false;
      for (int i = 0; i < merged.size(); i++) {
        if (merged.get(i).getId().equals(bNode.getId())) {
          merged.set(i, bNode);
          updated = true;
          break;
        }
      }

      if (!updated) {
        merged.add(bNode);
      }
    }

    return merged;
  }

}
