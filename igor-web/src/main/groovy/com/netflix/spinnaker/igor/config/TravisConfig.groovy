/*
 * Copyright 2016 Schibsted ASA.
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
import com.netflix.spinnaker.igor.service.BuildMasters
import com.netflix.spinnaker.igor.travis.TravisCache
import com.netflix.spinnaker.igor.travis.client.TravisClient
import com.netflix.spinnaker.igor.travis.service.TravisService
import com.squareup.okhttp.OkHttpClient
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit.Endpoints
import retrofit.RequestInterceptor
import retrofit.RestAdapter
import retrofit.client.OkClient

import javax.validation.Valid
import java.util.concurrent.TimeUnit


/**
 * Converts the list of Travis Configuration properties a collection of clients to access the Travis hosts
 */
@Configuration
@Slf4j
@CompileStatic
@ConditionalOnProperty("travis.enabled")
@EnableConfigurationProperties(TravisProperties)
class TravisConfig {

    @Bean
    Map<String, TravisService> travisMasters(BuildMasters buildMasters, TravisCache travisCache, IgorConfigurationProperties igorConfigurationProperties, @Valid TravisProperties travisProperties) {
        log.info "creating travisMasters"
        Map<String, TravisService> travisMasters = (travisProperties?.masters?.collectEntries { TravisProperties.TravisHost host ->
            String travisName = "travis-${host.name}"
            log.info "bootstrapping ${host.address} as ${travisName}"

            [(travisName): travisService(travisName, host.baseUrl, host.githubToken, travisClient(host.address, igorConfigurationProperties.client.timeout), travisCache)]
        })
        buildMasters.map.putAll travisMasters
        travisMasters
    }

    static TravisService travisService(String travisHostId, String baseUrl, String githubToken, TravisClient travisClient, TravisCache travisCache) {
        return new TravisService(travisHostId, baseUrl, githubToken, travisClient, travisCache)
    }

    static TravisClient travisClient(String address, int timeout = 30000) {
        OkHttpClient client = new OkHttpClient()
        client.setReadTimeout(timeout, TimeUnit.MILLISECONDS)

        //Need this code because without FULL log level, fetching logs will fail. Ref https://github.com/square/retrofit/issues/953.
        RestAdapter.Log fooLog = new RestAdapter.Log() {
            @Override public void log(String message) {
            }
        }
        new RestAdapter.Builder()
            .setEndpoint(Endpoints.newFixedEndpoint(address))
            .setRequestInterceptor(new TravisHeader())
            .setClient(new OkClient(client))
            .setLog(fooLog)
            .setLogLevel(RestAdapter.LogLevel.FULL)
            .build()
            .create(TravisClient)
    }

    static class TravisHeader implements RequestInterceptor {

        @Override
        void intercept(RequestInterceptor.RequestFacade request) {
            request.addHeader("Accept", "application/vnd.travis-ci.2+json")
            request.addHeader("User-Agent", "Travis-Igor")
            request.addHeader("Content-Type", "application/json")

        }
    }
}
