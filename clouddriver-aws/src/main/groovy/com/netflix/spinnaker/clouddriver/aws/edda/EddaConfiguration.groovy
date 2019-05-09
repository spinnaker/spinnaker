/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.edda

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.awsobjectmapper.AmazonObjectMapper
import com.netflix.awsobjectmapper.AmazonObjectMapperConfigurer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit.converter.Converter
import retrofit.converter.JacksonConverter

@Configuration
class EddaConfiguration {
  @Bean
  Converter eddaConverter() {
    new JacksonConverter(AmazonObjectMapperConfigurer.createConfigured()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES))
  }

  @Bean
  EddaApiFactory eddaApiFactory(Converter eddaConverter) {
    new EddaApiFactory(eddaConverter)
  }

}
