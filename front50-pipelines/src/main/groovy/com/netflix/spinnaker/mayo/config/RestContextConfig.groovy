/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
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

package com.netflix.spinnaker.mayo.config

import com.fasterxml.jackson.databind.DeserializationFeature
import cz.jirutka.spring.exhandler.RestHandlerExceptionResolver
import cz.jirutka.spring.exhandler.support.HttpMessageConverterUtils
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.web.servlet.HandlerExceptionResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver

/**
 * Converts validation errors into REST Messages
 * Configures {@link com.fasterxml.jackson.databind.ObjectMapper} used to deserialize requests
 */
@Configuration
class RestContextConfig extends WebMvcConfigurationSupport {

    @Override
    void configureHandlerExceptionResolvers(List<HandlerExceptionResolver> resolvers) {
        resolvers.add(exceptionHandlerExceptionResolver()) // resolves @ExceptionHandler
        resolvers.add(restExceptionResolver())
    }

    @Override
    void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        super.addDefaultHttpMessageConverters(converters)
        MappingJackson2HttpMessageConverter converter =
            (MappingJackson2HttpMessageConverter) converters.find
                { it.class.isAssignableFrom(MappingJackson2HttpMessageConverter) }
        converter.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    @Bean
    RestHandlerExceptionResolver restExceptionResolver() {
        RestHandlerExceptionResolver.builder()
            .defaultContentType(MediaType.APPLICATION_JSON)
            .withDefaultHandlers(true)
            .build()
    }

    @Bean
    ExceptionHandlerExceptionResolver exceptionHandlerExceptionResolver() {
        ExceptionHandlerExceptionResolver resolver = new ExceptionHandlerExceptionResolver()
        resolver.setMessageConverters(HttpMessageConverterUtils.defaultHttpMessageConverters)
        resolver
    }
}
