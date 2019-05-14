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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.TemplateLoaderException;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/** Used strictly for testing, not available as a template loader at runtime. */
public class ResourceSchemeLoader implements TemplateSchemeLoader {

  private final String rootPath;

  private final ObjectMapper jsonObjectMapper;
  private final ObjectMapper yamlObjectMapper;

  public ResourceSchemeLoader(String rootPath, ObjectMapper objectMapper) {
    this.rootPath = rootPath;
    this.jsonObjectMapper = objectMapper;
    this.yamlObjectMapper =
        new ObjectMapper(new YAMLFactory())
            .setConfig(jsonObjectMapper.getSerializationConfig())
            .setConfig(jsonObjectMapper.getDeserializationConfig());
  }

  @Override
  public boolean supports(URI uri) {
    URI u = convertToResourcePath(uri);
    String scheme = u.getScheme();
    return scheme.equalsIgnoreCase("file") && (isJson(u) || isYaml(u));
  }

  @Override
  public PipelineTemplate load(URI uri) {
    URI u = convertToResourcePath(uri);

    File templateFile = new File(u);

    if (!templateFile.exists()) {
      throw new TemplateLoaderException(new FileNotFoundException(u.toString()));
    }

    try {
      ObjectMapper objectMapper = isJson(u) ? jsonObjectMapper : yamlObjectMapper;
      return objectMapper.readValue(templateFile, PipelineTemplate.class);
    } catch (IOException e) {
      throw new TemplateLoaderException(e);
    }
  }

  private URI convertToResourcePath(URI uri) {
    try {
      return getClass().getResource(rootPath + "/" + uri.toString()).toURI();
    } catch (URISyntaxException e) {
      throw new TemplateLoaderException(e);
    }
  }
}
