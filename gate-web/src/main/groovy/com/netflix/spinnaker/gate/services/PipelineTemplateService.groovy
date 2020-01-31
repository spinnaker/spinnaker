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
package com.netflix.spinnaker.gate.services

import com.fasterxml.jackson.annotation.JsonProperty
import com.netflix.spinnaker.gate.services.internal.Front50Service
import com.netflix.spinnaker.gate.services.internal.OrcaServiceSelector
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.Nonnull

@CompileStatic
@Component
@Slf4j
class PipelineTemplateService {

  private static final String GROUP = "pipelineTemplates"

  private final Front50Service front50Service

  private final OrcaServiceSelector orcaServiceSelector

  @Autowired
  public PipelineTemplateService(Front50Service front50Service, OrcaServiceSelector orcaServiceSelector) {
    this.front50Service = front50Service;
    this.orcaServiceSelector = orcaServiceSelector;
  }

  Map get(String id) {
    front50Service.getPipelineTemplate(id)
  }

  List<Map> findByScope(List<String> scopes) {
    front50Service.getPipelineTemplates((String[]) scopes?.toArray())
  }

  Map resolve(String source, String executionId, String pipelineConfigId) {
    orcaServiceSelector.select().resolvePipelineTemplate(source, executionId, pipelineConfigId)
  }

  List<PipelineTemplateDependent> getTemplateDependents(@Nonnull String templateId, boolean recursive) {
    front50Service.getPipelineTemplateDependents(templateId, recursive).collect {
      new PipelineTemplateDependent(
        application: (String) it.application ?: "UNKNOWN",
        pipelineConfigId: (String) it.id ?: "UNKNOWN",
        pipelineName: (String) it.name ?: "UNKNOWN"
      )
    }
  }

  static class PipelineTemplateDependent {
    PipelineTemplateDependent() {}

    PipelineTemplateDependent(String application, String pipelineConfigId, String pipelineName) {
      this.application = application
      this.pipelineConfigId = pipelineConfigId
      this.pipelineName = pipelineName
    }

    @JsonProperty
    String application

    @JsonProperty
    String pipelineConfigId

    @JsonProperty
    String pipelineName
  }
}
