/*
 * Copyright 2026 Netflix, Inc.
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
package com.netflix.kayenta.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Boot 4's MVC JSON converter binds the primary {@link JsonMapper}. When Kayenta embeds Orca that
 * primary is Orca's mapper, which does not know Kayenta subtypes, so requests such as {@code POST
 * /standalone_canary_analysis} fail to read. Put Kayenta's configured mapper first.
 *
 * <p>Separate from {@link KayentaConfiguration} so injecting {@code kayentaObjectMapper} cannot
 * cycle with the configuration that defines it.
 */
@Configuration
public class KayentaJacksonMessageConverterConfiguration implements WebMvcConfigurer {

  private final JsonMapper kayentaObjectMapper;

  public KayentaJacksonMessageConverterConfiguration(
      @Qualifier("kayentaObjectMapper") ObjectMapper kayentaObjectMapper) {
    this.kayentaObjectMapper = (JsonMapper) kayentaObjectMapper;
  }

  @Override
  public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
    converters.add(0, new JacksonJsonHttpMessageConverter(kayentaObjectMapper));
  }
}
