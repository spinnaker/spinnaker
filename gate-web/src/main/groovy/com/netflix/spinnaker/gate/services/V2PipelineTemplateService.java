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

package com.netflix.spinnaker.gate.services;

import com.netflix.spinnaker.gate.services.PipelineTemplateService.PipelineTemplateDependent;
import com.netflix.spinnaker.gate.services.internal.Front50Service;
import com.netflix.spinnaker.gate.services.internal.OrcaServiceSelector;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class V2PipelineTemplateService {
  private final Front50Service front50Service;

  private final OrcaServiceSelector orcaServiceSelector;

  @Autowired
  public V2PipelineTemplateService(
      Front50Service front50Service, OrcaServiceSelector orcaServiceSelector) {
    this.front50Service = front50Service;
    this.orcaServiceSelector = orcaServiceSelector;
  }

  public Map get(String id, String tag, String digest) {
    return front50Service.getV2PipelineTemplate(id, tag, digest);
  }

  public Map<String, Object> plan(Map<String, Object> pipeline) {
    return orcaServiceSelector.select().plan(pipeline);
  }

  // TODO(louisjimenez): Deprecated. Will be replaced with /versions endpoint starting with 1.19.
  public List<Map> findByScope(List<String> scopes) {
    return front50Service.getV2PipelineTemplates(
        scopes == null ? null : (String[]) scopes.toArray());
  }

  public Map<String, List<Map>> findVersionsByScope(List<String> scopes) {
    return front50Service.getV2PipelineTemplatesVersions(
        scopes == null ? null : (String[]) scopes.toArray());
  }

  public List<PipelineTemplateDependent> getTemplateDependents(@Nonnull String templateId) {
    return front50Service.getV2PipelineTemplateDependents(templateId).stream()
        .map(t -> newDependent(t))
        .collect(Collectors.toList());
  }

  private static PipelineTemplateDependent newDependent(Map<String, Object> template) {
    return new PipelineTemplateDependent(
        template.containsKey("application") ? (String) template.get("application") : "UNKNOWN",
        template.containsKey("id") ? (String) template.get("id") : "UNKNOWN",
        template.containsKey("name") ? (String) template.get("name") : "UNKNOWN");
  }
}
