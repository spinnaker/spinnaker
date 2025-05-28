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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.TemplateLoaderException;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class Front50SchemeLoader implements TemplateSchemeLoader {
  private final Front50Service front50Service;

  private final ObjectMapper objectMapper;

  @Autowired
  public Front50SchemeLoader(
      Optional<Front50Service> front50Service, ObjectMapper pipelineTemplateObjectMapper) {
    this.front50Service = front50Service.orElse(null);
    this.objectMapper = pipelineTemplateObjectMapper;
  }

  @Override
  public boolean supports(URI uri) {
    String scheme = uri.getScheme();
    return scheme.equalsIgnoreCase("spinnaker");
  }

  @Override
  public Map<String, Object> load(URI uri) {
    if (front50Service == null) {
      throw new TemplateLoaderException(
          "Cannot load templates without front50 enabled. Set 'front50.enabled: true' in your orca config.");
    }

    String id = uri.getRawAuthority();
    try {
      Map<String, Object> pipelineTemplate =
          AuthenticatedRequest.allowAnonymous(
              () -> Retrofit2SyncCall.execute(front50Service.getPipelineTemplate(id)));
      return objectMapper.convertValue(pipelineTemplate, new TypeReference<>() {});
    } catch (Exception e) {
      throw new TemplateLoaderException(e);
    }
  }
}
