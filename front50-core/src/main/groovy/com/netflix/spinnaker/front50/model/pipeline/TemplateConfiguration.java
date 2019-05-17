/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.front50.model.pipeline;

public class TemplateConfiguration {

  private PipelineDefinition pipeline;

  public PipelineDefinition getPipeline() {
    return pipeline;
  }

  public void setPipeline(PipelineDefinition pipeline) {
    this.pipeline = pipeline;
  }

  public static class PipelineDefinition {

    private TemplateSource template;

    public TemplateSource getTemplate() {
      return template;
    }

    public void setTemplate(TemplateSource template) {
      this.template = template;
    }
  }

  public static class TemplateSource {

    public static final String SPINNAKER_PREFIX = "spinnaker://";

    private String source;

    public String getSource() {
      return source;
    }

    public void setSource(String source) {
      this.source = source;
    }
  }
}
