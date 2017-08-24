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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class HttpTemplateSchemeLoader implements TemplateSchemeLoader {
  private final Logger log = LoggerFactory.getLogger(getClass());

  private final ObjectMapper jsonObjectMapper;
  private final ObjectMapper yamlObjectMapper;

  @Autowired
  public HttpTemplateSchemeLoader(ObjectMapper pipelineTemplateObjectMapper) {
    this.jsonObjectMapper = pipelineTemplateObjectMapper;

    this.yamlObjectMapper = new ObjectMapper(new YAMLFactory())
      .setConfig(jsonObjectMapper.getSerializationConfig())
      .setConfig(jsonObjectMapper.getDeserializationConfig());
  }

  @Override
  public boolean supports(URI uri) {
    String scheme = uri.getScheme();
    return scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https");
  }

  @Override
  public PipelineTemplate load(URI uri) {
    try (InputStream is = uri.toURL().openConnection().getInputStream();
         BufferedReader reader = new BufferedReader(new InputStreamReader(is));
         Stream<String> stream = reader.lines()) {
      ObjectMapper objectMapper = isJson(uri) ? jsonObjectMapper : yamlObjectMapper;

      String template = stream.collect(Collectors.joining("\n"));
      log.debug("Loaded Template ({}):\n{}", uri, template);

      return objectMapper.readValue(template, PipelineTemplate.class);
    } catch (Exception e) {
      throw new TemplateLoaderException(e);
    }
  }
}
