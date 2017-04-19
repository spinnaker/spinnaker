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
package com.netflix.spinnaker.orca.pipelinetemplate;

import com.netflix.spinnaker.orca.pipelinetemplate.loader.TemplateLoader;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.TemplateMerge;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration.TemplateSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PipelineTemplateService {

  private final TemplateLoader templateLoader;

  @Autowired
  public PipelineTemplateService(TemplateLoader templateLoader) {
    this.templateLoader = templateLoader;
  }

  public PipelineTemplate resolveTemplate(TemplateSource templateSource) {
    List<PipelineTemplate> templates = templateLoader.load(templateSource);
    return TemplateMerge.merge(templates);
  }
}
