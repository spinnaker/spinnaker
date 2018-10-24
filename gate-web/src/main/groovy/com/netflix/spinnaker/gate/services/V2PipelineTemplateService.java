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
  public V2PipelineTemplateService(Front50Service front50Service, OrcaServiceSelector orcaServiceSelector) {
    this.front50Service = front50Service;
    this.orcaServiceSelector = orcaServiceSelector;
  }

  // TODO(jacobkiefer): Un-stub
  public Map get(String id) {
    return front50Service.getV2PipelineTemplate(id);
  }

  public Map resolve(String source, String executionId, String pipelineConfigId) {
    return null;
  }

  public List<Map> findByScope(List<String> scopes) {
    return front50Service.getV2PipelineTemplates(scopes == null ? null : (String[]) scopes.toArray());
  }

  public List<PipelineTemplateDependent> getTemplateDependents(@Nonnull String templateId, boolean recursive) {
    return null;
  }
}
