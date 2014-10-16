/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.config

import static retrofit.Endpoints.newFixedEndpoint

import com.netflix.spinnaker.echo.events.OrcaService
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import retrofit.RestAdapter
import retrofit.client.Client

/**
 * Orca Config
 */
@Configuration
@Import(RetrofitConfiguration)
@ConditionalOnProperty(value = 'orca.baseUrl')
@CompileStatic
@SuppressWarnings('GStringExpressionWithinString')
class OrcaConfiguration {

    @Autowired
    Client retrofitClient

    @Autowired
    RestAdapter.LogLevel retrofitLogLevel

    @Bean
    OrcaService orcaService(@Value('${orca.baseUrl:http://orca.prod.netflix.net}') String orcaBaseUrl) {
        new RestAdapter.Builder()
            .setEndpoint(newFixedEndpoint(orcaBaseUrl))
            .setClient(retrofitClient)
            .setLogLevel(retrofitLogLevel)
            .build()
            .create(OrcaService)
    }
}
