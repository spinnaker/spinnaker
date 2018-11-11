/*
 * Copyright 2017 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.webhook.config

import ch.qos.logback.classic.pattern.MessageConverter
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.StringHttpMessageConverter
import org.springframework.web.client.RestTemplate

@Configuration
@ConditionalOnProperty(prefix = "webhook.stage", value = "enabled", matchIfMissing = true)
@ComponentScan("com.netflix.spinnaker.orca.webhook")
@EnableConfigurationProperties(PreconfiguredWebhookProperties)
class WebhookConfiguration {

  @Bean
  @ConditionalOnMissingBean(RestTemplate)
  RestTemplate restTemplate() {
    RestTemplate restTemplate = new RestTemplate()
    List<MessageConverter> converters = restTemplate.getMessageConverters()
    converters.add(new ObjectStringHttpMessageConverter())
    restTemplate.setMessageConverters(converters)
    HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
    restTemplate.setRequestFactory(requestFactory)
    return restTemplate
  }

  class ObjectStringHttpMessageConverter extends StringHttpMessageConverter implements HttpMessageConverter<Object> {
    @Override
    boolean supports(Class<?> clazz) {
      return Object.class.equals(clazz)
    }
  }
}
