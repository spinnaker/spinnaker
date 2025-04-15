/*
 * Copyright 2015 Netflix, Inc.
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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.config.OkHttp3ClientConfiguration
import com.netflix.spinnaker.igor.scm.github.client.GitHubClient
import com.netflix.spinnaker.igor.scm.github.client.GitHubMaster
import com.netflix.spinnaker.igor.util.RetrofitUtils
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory

import javax.validation.Valid

/**
 * Converts the list of GitHub Configuration properties a collection of clients to access the GitHub hosts
 */
@Configuration
@ConditionalOnProperty('github.base-url')
@Slf4j
@CompileStatic
@EnableConfigurationProperties(GitHubProperties)
class GitHubConfig {

    @Bean
    GitHubMaster gitHubMasters(@Valid GitHubProperties gitHubProperties, ObjectMapper mapper, OkHttp3ClientConfiguration okHttpClientConfig) {
        log.info "bootstrapping ${gitHubProperties.baseUrl} as github"
        new GitHubMaster(gitHubClient: gitHubClient(okHttpClientConfig, gitHubProperties.baseUrl, gitHubProperties.accessToken, mapper), baseUrl: gitHubProperties.baseUrl)
    }

    GitHubClient gitHubClient(OkHttp3ClientConfiguration okHttpClientConfig, String address, String accessToken, ObjectMapper mapper = new ObjectMapper()) {
        new Retrofit.Builder()
            .baseUrl(RetrofitUtils.getBaseUrl(address))
            .client(okHttpClientConfig.createForRetrofit2().addInterceptor(new BasicAuthRequestInterceptor(accessToken)).build())
            .addConverterFactory(JacksonConverterFactory.create(mapper))
            .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
            .build()
            .create(GitHubClient)

    }

    static class BasicAuthRequestInterceptor implements Interceptor {

        private final String accessToken

        BasicAuthRequestInterceptor(String accessToken) {
            this.accessToken = accessToken
        }

        @Override
        Response intercept(Chain chain) {
          Request request = chain.request().newBuilder().addHeader("Authorization", "token " + accessToken).build()
          return chain.proceed(request)
        }
    }

}
