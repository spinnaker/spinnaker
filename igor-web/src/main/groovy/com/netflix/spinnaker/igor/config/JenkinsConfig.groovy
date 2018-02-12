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

import com.netflix.spinnaker.igor.IgorConfigurationProperties
import com.netflix.spinnaker.igor.config.auth.AuthRequestInterceptor
import com.netflix.spinnaker.igor.jenkins.client.JenkinsClient
import com.netflix.spinnaker.igor.jenkins.service.JenkinsService
import com.netflix.spinnaker.igor.service.BuildMasters
import com.squareup.okhttp.OkHttpClient
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit.Endpoints
import retrofit.RestAdapter
import retrofit.client.OkClient
import retrofit.converter.SimpleXMLConverter

import javax.validation.Valid
import java.util.concurrent.TimeUnit

/**
 * Converts the list of Jenkins Configuration properties a collection of clients to access the Jenkins hosts
 */
@Configuration
@Slf4j
@CompileStatic
@ConditionalOnProperty("jenkins.enabled")
@EnableConfigurationProperties(JenkinsProperties)
class JenkinsConfig {

    @Bean
    Map<String, JenkinsService> jenkinsMasters(BuildMasters buildMasters, IgorConfigurationProperties igorConfigurationProperties, @Valid JenkinsProperties jenkinsProperties) {
        log.info "creating jenkinsMasters"
        Map<String, JenkinsService> jenkinsMasters = ( jenkinsProperties?.masters?.collectEntries { JenkinsProperties.JenkinsHost host ->
            log.info "bootstrapping ${host.address} as ${host.name}"
            [(host.name): jenkinsService(host.name, jenkinsClient(host, igorConfigurationProperties.client.timeout), host.csrf)]
        })

        buildMasters.map.putAll jenkinsMasters
        jenkinsMasters
    }

    static JenkinsService jenkinsService(String jenkinsHostId, JenkinsClient jenkinsClient, Boolean csrf) {
        return new JenkinsService(jenkinsHostId, jenkinsClient, csrf)
    }

    static JenkinsClient jenkinsClient(JenkinsProperties.JenkinsHost host, int timeout = 30000) {
        OkHttpClient client = new OkHttpClient()
        client.setReadTimeout(timeout, TimeUnit.MILLISECONDS)

        new RestAdapter.Builder()
            .setEndpoint(Endpoints.newFixedEndpoint(host.address))
            .setRequestInterceptor(new AuthRequestInterceptor(host))
            .setClient(new OkClient(client))
            .setConverter(new SimpleXMLConverter())
            .build()
            .create(JenkinsClient)
    }
}
