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

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.fiat.model.resources.Permissions
import com.netflix.spinnaker.igor.IgorConfigurationProperties
import com.netflix.spinnaker.igor.config.client.DefaultJenkinsOkHttpClientProvider
import com.netflix.spinnaker.igor.config.client.DefaultJenkinsRetrofitRequestInterceptorProvider
import com.netflix.spinnaker.igor.config.client.JenkinsOkHttpClientProvider
import com.netflix.spinnaker.igor.config.client.JenkinsRetrofitRequestInterceptorProvider
import com.netflix.spinnaker.igor.jenkins.client.JenkinsClient
import com.netflix.spinnaker.igor.jenkins.service.JenkinsService
import com.netflix.spinnaker.igor.service.BuildServices
import com.netflix.spinnaker.okhttp.OkHttpMetricsInterceptor
import com.netflix.spinnaker.retrofit.Slf4jRetrofitLogger
import com.squareup.okhttp.OkHttpClient
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit.Endpoints
import retrofit.RequestInterceptor
import retrofit.RestAdapter
import retrofit.client.OkClient
import retrofit.converter.JacksonConverter

import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import javax.validation.Valid
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
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
    @ConditionalOnMissingBean
    JenkinsOkHttpClientProvider jenkinsOkHttpClientProvider() {
        return new DefaultJenkinsOkHttpClientProvider()
    }

    @Bean
    @ConditionalOnMissingBean
    JenkinsRetrofitRequestInterceptorProvider jenkinsRetrofitRequestInterceptorProvider() {
        return new DefaultJenkinsRetrofitRequestInterceptorProvider()
    }

    @Bean
    Map<String, JenkinsService> jenkinsMasters(BuildServices buildServices,
                                               IgorConfigurationProperties igorConfigurationProperties,
                                               @Valid JenkinsProperties jenkinsProperties,
                                               JenkinsOkHttpClientProvider jenkinsOkHttpClientProvider,
                                               JenkinsRetrofitRequestInterceptorProvider jenkinsRetrofitRequestInterceptorProvider,
                                               Registry registry,
                                               CircuitBreakerRegistry circuitBreakerRegistry) {
        log.info "creating jenkinsMasters"
        Map<String, JenkinsService> jenkinsMasters = jenkinsProperties?.masters?.collectEntries { JenkinsProperties.JenkinsHost host ->
            log.info "bootstrapping ${host.address} as ${host.name}"
            [(host.name): jenkinsService(
                host.name,
                jenkinsClient(
                    host,
                    jenkinsOkHttpClientProvider.provide(host),
                    jenkinsRetrofitRequestInterceptorProvider.provide(host),
                    registry,
                    igorConfigurationProperties.client.timeout
                ),
                host.csrf,
                host.permissions.build(),
                circuitBreakerRegistry
            )]
        }

        buildServices.addServices(jenkinsMasters)
        jenkinsMasters
    }

    static JenkinsService jenkinsService(
      String jenkinsHostId,
      JenkinsClient jenkinsClient,
      Boolean csrf,
      Permissions permissions,
      CircuitBreakerRegistry circuitBreakerRegistry
    ) {
        return new JenkinsService(jenkinsHostId, jenkinsClient, csrf, permissions, circuitBreakerRegistry)
    }

    static ObjectMapper getObjectMapper() {
        return new XmlMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(new JaxbAnnotationModule())
    }

    static JenkinsClient jenkinsClient(JenkinsProperties.JenkinsHost host,
                                       OkHttpClient client,
                                       RequestInterceptor requestInterceptor,
                                       Registry registry,
                                       int timeout = 30000) {
        client.setReadTimeout(timeout, TimeUnit.MILLISECONDS)

        if (host.skipHostnameVerification) {
            client.setHostnameVerifier({ hostname, _ ->
                true
            })
        }

        TrustManager[] trustManagers = null
        KeyManager[] keyManagers = null

        if (host.trustStore) {
            if (host.trustStore.equals("*")) {
                trustManagers = [new TrustAllTrustManager()]
            } else {
                def trustStorePassword = host.trustStorePassword
                def trustStore = KeyStore.getInstance(host.trustStoreType)
                new File(host.trustStore).withInputStream {
                    trustStore.load(it, trustStorePassword.toCharArray())
                }
                def trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                trustManagerFactory.init(trustStore)

                trustManagers = trustManagerFactory.trustManagers
            }
        }

        if (host.keyStore) {
            def keyStorePassword = host.keyStorePassword
            def keyStore = KeyStore.getInstance(host.keyStoreType)
            new File(host.keyStore).withInputStream {
                keyStore.load(it, keyStorePassword.toCharArray())
            }
            def keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            keyManagerFactory.init(keyStore, keyStorePassword.toCharArray())

            keyManagers = keyManagerFactory.keyManagers
        }

        if (trustManagers || keyManagers) {
            def sslContext = SSLContext.getInstance("TLS")
            sslContext.init(keyManagers, trustManagers, null)

            client.setSslSocketFactory(sslContext.socketFactory)
        }

        if (registry == null) {
            log.warn("no registry provided, OkHttpMetricsInterceptor will not be created for JenkinsClient")
        } else {
            client.interceptors().add(new OkHttpMetricsInterceptor({ -> registry }, true))
        }

        new RestAdapter.Builder()
            .setEndpoint(Endpoints.newFixedEndpoint(host.address))
            .setRequestInterceptor(new RequestInterceptor() {
                @Override
                void intercept(RequestInterceptor.RequestFacade request) {
                    request.addHeader("User-Agent", "Spinnaker-igor")
                    requestInterceptor.intercept(request)
                }
            })
            .setLogLevel(RestAdapter.LogLevel.BASIC)
            .setClient(new OkClient(client))
            .setConverter(new JacksonConverter(getObjectMapper()))
            .setLog(new Slf4jRetrofitLogger(JenkinsClient))
            .build()
            .create(JenkinsClient)
    }

    static JenkinsClient jenkinsClient(JenkinsProperties.JenkinsHost host, Registry registry = null, int timeout = 30000) {
        OkHttpClient client = new OkHttpClient()
        jenkinsClient(host, client, RequestInterceptor.NONE, registry, timeout)
    }

    static class TrustAllTrustManager implements X509TrustManager {
        @Override
        void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
            // do nothing
        }

        @Override
        void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
            // do nothing
        }

        @Override
        X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0]
        }
    }

}
