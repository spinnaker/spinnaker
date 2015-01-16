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

import com.netflix.spinnaker.igor.jenkins.client.JenkinsClient
import com.netflix.spinnaker.igor.jenkins.client.JenkinsMasters
import com.squareup.okhttp.Authenticator
import com.squareup.okhttp.Credentials
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Request
import com.squareup.okhttp.Response
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit.Endpoints
import retrofit.RequestInterceptor
import retrofit.RestAdapter
import retrofit.client.OkClient
import retrofit.converter.SimpleXMLConverter

import javax.validation.Valid

/**
 * Converts the list of Jenkins Configuration properties a collection of clients to access the Jenkins hosts
 */
@Configuration
@Slf4j
@CompileStatic
class JenkinsConfig {

    @Bean
    JenkinsMasters jenkinsMasters(@Valid JenkinsProperties jenkinsProperties) {
        new JenkinsMasters(map: jenkinsProperties?.masters?.collectEntries { JenkinsProperties.JenkinsHost host ->
            log.info "bootstrapping ${host.address} as ${host.name}"
            [(host.name): jenkinsClient(host.address, host.username, host.password)]
        })
    }

    JenkinsClient jenkinsClient(String address, String username, String password) {
        new RestAdapter.Builder()
            .setEndpoint(Endpoints.newFixedEndpoint(address))
            .setRequestInterceptor(new BasicAuthRequestInterceptor(username, password))
            .setClient(new OkClient())
            .setConverter(new SimpleXMLConverter())
            .build()
            .create(JenkinsClient)

    }

    static class BasicAuthRequestInterceptor implements RequestInterceptor {

        private final String username
        private final String password

        BasicAuthRequestInterceptor(String username, String password) {
            this.username = username
            this.password = password
        }

        @Override
        void intercept(RequestInterceptor.RequestFacade request) {
            request.addHeader("Authorization", Credentials.basic(username, password))
        }
    }

}
