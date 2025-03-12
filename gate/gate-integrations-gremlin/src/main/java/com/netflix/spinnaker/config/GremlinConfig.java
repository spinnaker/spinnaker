/*
 * Copyright 2019 Gremlin, Inc.
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

package com.netflix.spinnaker.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.gate.config.Service;
import com.netflix.spinnaker.gate.config.ServiceConfiguration;
import com.netflix.spinnaker.gate.services.gremlin.GremlinService;
import com.netflix.spinnaker.kork.client.ServiceClientProvider;
import com.netflix.spinnaker.okhttp.SpinnakerRequestHeaderInterceptor;
import groovy.transform.CompileStatic;
import groovy.util.logging.Slf4j;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@CompileStatic
@Configuration
@ConditionalOnProperty("integrations.gremlin.enabled")
class GremlinConfig {
  @Bean
  GremlinService gremlinService(
      ServiceConfiguration serviceConfiguration,
      SpinnakerRequestHeaderInterceptor spinnakerHeaderRequestInterceptor,
      ServiceClientProvider serviceClientProvider) {

    Service service = serviceConfiguration.getService("gremlin");
    if (service == null) {
      throw new IllegalArgumentException(
          "Unknown service ${serviceName} requested of type ${type}");
    }

    if (!service.isEnabled()) {
      return null;
    }

    ObjectMapper objectMapper =
        new ObjectMapper()
            .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    return serviceClientProvider.getService(
        GremlinService.class,
        new DefaultServiceEndpoint("gremlin", service.getBaseUrl()),
        objectMapper,
        List.of(spinnakerHeaderRequestInterceptor));
  }
}
