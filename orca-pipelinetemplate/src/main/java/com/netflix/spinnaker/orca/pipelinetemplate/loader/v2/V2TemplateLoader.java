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

package com.netflix.spinnaker.orca.pipelinetemplate.loader.v2;

import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.TemplateLoaderException;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration;
import com.netflix.spinnaker.orca.pipelinetemplate.v2schema.model.V2PipelineTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;

import static java.lang.String.format;

@Component
public class V2TemplateLoader {
  private Collection<V2TemplateSchemeLoader> schemeLoaders;

  // TODO(jacobkiefer): Use Artifact resolution instead of custom template loaders.
  @Autowired
  public V2TemplateLoader(Collection<V2TemplateSchemeLoader> schemeLoaders) {
    this.schemeLoaders = schemeLoaders;
  }

  /**
   * @return a LIFO list of pipeline templates
   */
  public V2PipelineTemplate load(TemplateConfiguration.TemplateSource source) {
    return load(source.getSource());
  }

  private V2PipelineTemplate load(String source) {
    URI uri;
    try {
      uri = new URI(source);
    } catch (URISyntaxException e) {
      throw new TemplateLoaderException(format("Invalid URI '%s'", source), e);
    }

    V2TemplateSchemeLoader schemeLoader = schemeLoaders.stream()
      .filter(l -> l.supports(uri))
      .findFirst()
      .orElseThrow(() -> new TemplateLoaderException(format("No TemplateSchemeLoader found for '%s'", uri.getScheme())));

    return schemeLoader.load(uri);
  }
}
