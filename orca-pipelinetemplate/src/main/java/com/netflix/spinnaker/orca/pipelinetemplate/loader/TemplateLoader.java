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

package com.netflix.spinnaker.orca.pipelinetemplate.loader;

import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.TemplateLoaderException;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.lang.String.format;

@Component
public class TemplateLoader {
  private Collection<TemplateSchemeLoader> schemeLoaders;

  @Autowired
  public TemplateLoader(Collection<TemplateSchemeLoader> schemeLoaders) {
    this.schemeLoaders = schemeLoaders;
  }

  /**
   * @return a LIFO list of pipeline templates
   */
  public List<PipelineTemplate> load(TemplateConfiguration.TemplateSource template) {
    List<PipelineTemplate> pipelineTemplates = new ArrayList<>();

    PipelineTemplate pipelineTemplate = load(template.getSource());
    pipelineTemplates.add(0, pipelineTemplate);

    Set<String> seenTemplateSources = new HashSet<>();
    while (pipelineTemplate.getSource() != null) {
      seenTemplateSources.add(pipelineTemplate.getSource());

      pipelineTemplate = load(pipelineTemplate.getSource());
      pipelineTemplates.add(0, pipelineTemplate);

      if (seenTemplateSources.contains(pipelineTemplate.getSource())) {
        throw new TemplateLoaderException(
          format("Illegal cycle detected loading pipeline template '%s'", pipelineTemplate.getSource())
        );
      }
    }

    return pipelineTemplates;
  }

  private PipelineTemplate load(String source) {
    URI uri;
    try {
      uri = new URI(source);
    } catch (URISyntaxException e) {
      throw new TemplateLoaderException(format("Invalid URI '%s'", source), e);
    }

    TemplateSchemeLoader schemeLoader = schemeLoaders.stream()
      .filter(l -> l.supports(uri))
      .findFirst()
      .orElseThrow(() -> new TemplateLoaderException(format("No TemplateSchemeLoader found for '%s'", uri.getScheme())));

    return schemeLoader.load(uri);
  }
}
