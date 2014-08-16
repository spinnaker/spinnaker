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

package com.netflix.spinnaker.gate.config

import com.netflix.zuul.DynamicCodeCompiler
import com.netflix.zuul.FilterFileManager
import com.netflix.zuul.FilterLoader
import com.netflix.zuul.ZuulFilter
import com.netflix.zuul.context.ContextLifecycleFilter
import com.netflix.zuul.filters.FilterRegistry
import com.netflix.zuul.groovy.GroovyCompiler
import com.netflix.zuul.groovy.GroovyFileFilter
import com.netflix.zuul.http.ZuulServlet
import com.netflix.zuul.monitoring.MonitoringHelper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.embedded.FilterRegistrationBean
import org.springframework.boot.context.embedded.ServletRegistrationBean
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn

import javax.annotation.PostConstruct

@Configuration
class ZuulConfig {

    @Autowired
    ApplicationContext applicationContext

    @Autowired
    FilterRegistry filterRegistry

    @Bean
    String apiPrefix(@Value('${api.prefix:/api/v1/}') valueFromConfig) {
        def prefix = valueFromConfig ?: '/'
        if (!prefix.endsWith('/')) {
            prefix + '/'
        } else {
            prefix
        }
    }

    @Bean
    ServletRegistrationBean zuulServlet(String apiPrefix) {
        new ServletRegistrationBean(new ZuulServlet(), apiPrefix + '*')
    }

    @Bean
    FilterRegistrationBean contextLifecycleFilter(String apiPrefix) {
        def filter = new FilterRegistrationBean(new ContextLifecycleFilter())
        filter.addUrlPatterns(apiPrefix + '*')
        filter
    }

    @Bean
    FilterRegistry filterRegistry() {
        FilterRegistry.instance()
    }

    @PostConstruct
    void startUpTheZuuls() {
        MonitoringHelper.initMocks()
        Map<String, ZuulFilter> filters = applicationContext.getBeansOfType(ZuulFilter)
        filters.each { k, v ->
            filterRegistry.put(k, v)
        }
    }

    @Bean
    DynamicCodeCompiler dynamicCodeCompiler() {
        new GroovyCompiler()
    }

    @Bean
    FilterLoader filterLoader(DynamicCodeCompiler dynamicCodeCompiler) {
        def loader = FilterLoader.instance
        loader.compiler = dynamicCodeCompiler
        loader
    }

    @Bean
    FilenameFilter zuulFilenameFilter() {
        new GroovyFileFilter()
    }

    @Bean
    @DependsOn('filterLoader')
    FilterFileManager filterFileManager(@Value('${zuul.filter.pollInterval:5}') int pollIntervalSeconds,
                                        @Value('${zuul.filter.root:src/main/filters}') String filterRoot,
                                        @Value('${zuul.filter.pre:pre}') String preFilters,
                                        @Value('${zuul.filter.route:route') String routeFilters,
                                        @Value('${zuul.filter.post:post') String postFilters,
                                        FilenameFilter zuulFilenameFilter) {
        def rootDir = createReadableDirectory(filterRoot)
        def preFilterRoot = createReadableDirectory(rootDir, preFilters)
        def routeFilterRoot = createReadableDirectory(rootDir, routeFilters)
        def postFilterRoot = createReadableDirectory(rootDir, postFilters)
        new FilterFileManagerConfigurer(pollIntervalSeconds, preFilterRoot, routeFilterRoot, postFilterRoot, zuulFilenameFilter).createInstance()
    }

    static File createReadableDirectory(String path) {
        createReadableDirectory(null, path)
    }

    static File createReadableDirectory(File parent, String path) {
        def directory = parent == null ? new File(path) : new File(parent, path)
        if (!directory.exists()) {
            directory.mkdirs()
        }
        if (!(directory.exists() && directory.isDirectory() && directory.canRead())) {
            throw new IllegalArgumentException("Unable to create readable directory ${directory.canonicalPath}")
        }
        directory
    }


}
