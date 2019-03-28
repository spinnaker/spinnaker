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

package com.netflix.spinnaker.igor.config

import com.netflix.hystrix.exception.HystrixRuntimeException
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.fiat.shared.EnableFiatAutoConfig
import com.netflix.spinnaker.filters.AuthenticatedRequestFilter
import com.netflix.spinnaker.igor.artifacts.ArtifactServices
import com.netflix.spinnaker.igor.service.ArtifactDecorator
import com.netflix.spinnaker.igor.service.BuildServices
import com.netflix.spinnaker.kork.artifacts.parsing.DefaultJinjavaFactory
import com.netflix.spinnaker.kork.artifacts.parsing.JinjaArtifactExtractor
import com.netflix.spinnaker.kork.artifacts.parsing.JinjavaFactory
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.kork.web.interceptors.MetricsInterceptor
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter
import retrofit.RetrofitError

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Converts validation errors into REST Messages
 */
@Configuration
@CompileStatic
@Slf4j
@EnableFiatAutoConfig
class IgorConfig extends WebMvcConfigurerAdapter {
    @Autowired
    Registry registry

    @Autowired(required = false)
    ArtifactDecorator artifactDecorator

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(
            new MetricsInterceptor(
                this.registry, "controller.invocations", ["master"], ["BasicErrorController"]
            )
        )
    }

    @Bean
    FilterRegistrationBean authenticatedRequestFilter() {
        def frb = new FilterRegistrationBean(new AuthenticatedRequestFilter(true))
        frb.order = Ordered.HIGHEST_PRECEDENCE
        return frb
    }

    @Bean
    BuildServices buildServices() {
        new BuildServices()
    }

    @Bean
    ArtifactServices artifactServices() {
      new ArtifactServices()
    }

    @Bean
    ExecutorService executorService() {
        Executors.newCachedThreadPool()
    }

    @Bean
    HystrixRuntimeExceptionHandler hystrixRuntimeExceptionHandler() {
        return new HystrixRuntimeExceptionHandler()
    }

    @Bean
    RetrySupport retrySupport() {
        return new RetrySupport()
    }

    @Bean
    @ConditionalOnMissingBean
    JinjavaFactory jinjavaFactory() {
        return new DefaultJinjavaFactory();
    }

    @Bean
    JinjaArtifactExtractor.Factory jinjaArtifactExtractorFactory(JinjavaFactory jinjavaFactory) {
        return new JinjaArtifactExtractor.Factory(jinjavaFactory);
    }

    @ControllerAdvice
    static class HystrixRuntimeExceptionHandler {
        @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
        @ResponseBody
        @ExceptionHandler(HystrixRuntimeException)
        public Map handleHystrix(HystrixRuntimeException exception) {
            def failureCause = exception.cause
            if (failureCause instanceof RetrofitError) {
                failureCause = failureCause.cause ?: failureCause
            }

            return [
                fallbackException: exception.fallbackException.toString(),
                failureType: exception.failureType,
                failureCause: failureCause.toString(),
                error: "Hystrix Failure",
                message: exception.message,
                status: HttpStatus.TOO_MANY_REQUESTS.value(),
                timestamp: System.currentTimeMillis()
            ]
        }
    }
}
