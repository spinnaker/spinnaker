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
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.pipelinetemplate.v2schema.model.V2PipelineTemplate;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.client.Response;

@Component
public class V2TemplateLoader {

  private OortService oortService;

  private ObjectMapper objectMapper;

  @Autowired
  public V2TemplateLoader(OortService oortService, ObjectMapper objectMapper) {
    this.oortService = oortService;
    this.objectMapper = objectMapper;
  }

  public V2PipelineTemplate load(Artifact template) {
    Response templateContent = oortService.fetchArtifact(template);
    try {
      return objectMapper.readValue(templateContent.getBody().in(), V2PipelineTemplate.class);
    } catch (IOException ioe) {
      throw new IllegalArgumentException("Error resolving pipeline template: " + template, ioe);
    }
  }
}
