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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.TemplateLoaderException;
import com.netflix.spinnaker.orca.pipelinetemplate.v2schema.model.V2PipelineTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;

@Component
public class V2FileTemplateSchemeLoader implements V2TemplateSchemeLoader {
  private final ObjectMapper objectMapper;

  // TODO(jacobkiefer): Use Artifact resolution instead of custom template loaders.
  @Autowired
  public V2FileTemplateSchemeLoader(ObjectMapper pipelineTemplateObjectMapper) {
    this.objectMapper = pipelineTemplateObjectMapper;
  }

  @Override
  public boolean supports(URI uri) {
    String scheme = uri.getScheme();
    return scheme.equalsIgnoreCase("file");
  }

  @Override
  public V2PipelineTemplate load(URI uri) {
    File templateFile = new File(uri);

    if (!templateFile.exists()) {
      throw new TemplateLoaderException(new FileNotFoundException(uri.toString()));
    }

    try {
      return objectMapper.readValue(templateFile, V2PipelineTemplate.class);
    } catch (IOException e) {
      throw new TemplateLoaderException(e);
    }
  }
}
