/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.netflix.spinnaker.orca.pipelinetemplate.PipelineTemplateModule;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.HandlebarsRenderer;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.JinjaRenderer;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.JsonRenderedValueConverter;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.RenderedValueConverter;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.Renderer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

@ConditionalOnProperty("pipelineTemplate.enabled")
@ComponentScan(basePackageClasses = PipelineTemplateModule.class)
public class PipelineTemplateConfiguration {

  @Bean
  ObjectMapper pipelineTemplateObjectMapper() {
    return new ObjectMapper()
      .enable(SerializationFeature.INDENT_OUTPUT)
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  }

  @Bean
  RenderedValueConverter jsonRenderedValueConverter(ObjectMapper pipelineTemplateObjectMapper) {
    return new JsonRenderedValueConverter(pipelineTemplateObjectMapper);
  }

  @Bean
  @ConditionalOnExpression("!${pipelineTemplate.jinja.enabled:false}")
  Renderer handlebarsRenderer(RenderedValueConverter renderedValueConverter, ObjectMapper pipelineTemplateObjectMapper) {
    return new HandlebarsRenderer(renderedValueConverter, pipelineTemplateObjectMapper);
  }

  @Bean
  @ConditionalOnExpression("${pipelineTemplate.jinja.enabled:false}")
  Renderer jinjaRenderer(RenderedValueConverter renderedValueConverter, ObjectMapper pipelineTemplateObjectMapper) {
    return new JinjaRenderer(renderedValueConverter, pipelineTemplateObjectMapper);
  }
}
