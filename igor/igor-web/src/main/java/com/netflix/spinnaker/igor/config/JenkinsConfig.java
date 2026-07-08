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

package com.netflix.spinnaker.igor.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;
import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.igor.config.client.DefaultJenkinsRetrofitRequestInterceptorProvider;
import com.netflix.spinnaker.igor.config.client.JenkinsRetrofitRequestInterceptorProvider;
import com.netflix.spinnaker.igor.jenkins.client.JenkinsClient;
import com.netflix.spinnaker.igor.jenkins.service.JenkinsService;
import com.netflix.spinnaker.igor.service.BuildServices;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.kork.retrofit.util.RetrofitUtils;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import jakarta.validation.Valid;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Converts the list of Jenkins Configuration properties a collection of clients to access the Jenkins hosts
 */
@Configuration
@Slf4j
@ConditionalOnProperty("jenkins.enabled")
@EnableConfigurationProperties(JenkinsProperties.class)
public class JenkinsConfig {

    @Bean
    @ConditionalOnMissingBean
    public JenkinsRetrofitRequestInterceptorProvider jenkinsRetrofitRequestInterceptorProvider() {
        return new DefaultJenkinsRetrofitRequestInterceptorProvider();
    }

    @Bean
    public Map<String, JenkinsService> jenkinsMasters(BuildServices buildServices,
                                               IgorConfigurationProperties igorConfigurationProperties,
                                               @Valid JenkinsProperties jenkinsProperties,
                                               OkHttp3ClientConfiguration okHttpClientConfig,
                                               JenkinsRetrofitRequestInterceptorProvider jenkinsRetrofitRequestInterceptorProvider,
                                               CircuitBreakerRegistry circuitBreakerRegistry) {
        log.info("creating jenkinsMasters");
        Map<String, JenkinsService> jenkinsMasters = new HashMap<>();

        if (jenkinsProperties.getMasters() != null) {
            for (JenkinsProperties.JenkinsHost host : jenkinsProperties.getMasters()) {
                log.info("bootstrapping {} as {}", host.getAddress(), host.getName());
                jenkinsMasters.put(host.getName(), jenkinsService(
                    host.getName(),
                    jenkinsClient(
                        okHttpClientConfig,
                        host,
                        jenkinsRetrofitRequestInterceptorProvider,
                        igorConfigurationProperties.getClient().getTimeout()
                    ),
                    host.getCsrf(),
                    host.getPermissions().build(),
                    circuitBreakerRegistry
                ));
            }
        }

        buildServices.addServices(jenkinsMasters);
        return jenkinsMasters;
    }

    public static JenkinsService jenkinsService(
      String jenkinsHostId,
      JenkinsClient jenkinsClient,
      Boolean csrf,
      Permissions permissions,
      CircuitBreakerRegistry circuitBreakerRegistry
    ) {
        return new JenkinsService(jenkinsHostId, jenkinsClient, csrf, permissions, circuitBreakerRegistry);
    }

    public static ObjectMapper getObjectMapper() {
        return new XmlMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .registerModule(new JaxbAnnotationModule());
    }

  public static JenkinsClient jenkinsClient(OkHttp3ClientConfiguration okHttpClientConfig,
                                     JenkinsProperties.JenkinsHost host,
                                     JenkinsRetrofitRequestInterceptorProvider jenkinsRetrofitRequestInterceptorProvider,
                                     int timeout){

        Interceptor requestInterceptor = (jenkinsRetrofitRequestInterceptorProvider != null) ? jenkinsRetrofitRequestInterceptorProvider.provide(host): null;
        OkHttpClient.Builder clientBuilder = okHttpClientConfig.createForRetrofit2().readTimeout(timeout, TimeUnit.MILLISECONDS);
        if (requestInterceptor != null) {
          clientBuilder.addInterceptor(requestInterceptor);
        }
        clientBuilder.addInterceptor(new Interceptor() {
          @Override
          public Response intercept(Interceptor.Chain chain) throws IOException {
            Request request = chain.request().newBuilder().addHeader("User-Agent", "Spinnaker-igor").build();
            return chain.proceed(request);
          }
        });

        if (host.getSkipHostnameVerification()) {
          clientBuilder.hostnameVerifier((hostname, session) -> true);
        }

        TrustManager[] trustManagers = null;
        KeyManager[] keyManagers = null;

        if (host.getTrustStore() != null) {
            if (host.getTrustStore().equals("*")) {
                trustManagers = new TrustManager[]{new TrustAllTrustManager()};
            } else {
                trustManagers = createTrustStore(host.getTrustStore(), host.getTrustStorePassword(), host.getTrustStoreType());
            }
        }

        if (host.getKeyStore() != null) {
            try {
                String keyStorePassword = host.getKeyStorePassword();
                KeyStore keyStore = KeyStore.getInstance(host.getKeyStoreType());
                try (InputStream is = new FileInputStream(host.getKeyStore())) {
                    keyStore.load(is, keyStorePassword.toCharArray());
                }
                KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                keyManagerFactory.init(keyStore, keyStorePassword.toCharArray());

                keyManagers = keyManagerFactory.getKeyManagers();

                if (trustManagers == null) {
                    log.warn("{}: okhttp3 (unlike okhttp2) doesn't support configuring only a keystore without " +
                            "a truststore. Please configure a truststore to get rid of this message. Trying to use the " +
                            "keystore '{}' as a truststore as well. Your mileage may vary.", host.getName(), host.getKeyStore());
                    trustManagers = createTrustStore(host.getKeyStore(), host.getKeyStorePassword(), host.getKeyStoreType());
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to configure keystore", e);
            }
        }

        if (trustManagers != null || keyManagers != null) {
            try {
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(keyManagers, trustManagers, null);
                clientBuilder.sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustManagers[0]);
            } catch (Exception e) {
                throw new RuntimeException("Failed to configure SSL", e);
            }
        }

        return new Retrofit.Builder()
            .baseUrl(RetrofitUtils.getBaseUrl(host.getAddress()))
            .client(clientBuilder.build())
            .addConverterFactory(JacksonConverterFactory.create(getObjectMapper()))
            .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
            .build()
            .create(JenkinsClient.class);
    }

    private static TrustManager[] createTrustStore(String trustStoreName,
                                                   String trustStorePassword,
                                                   String trustStoreType) {
        try {
            KeyStore trustStore = KeyStore.getInstance(trustStoreType);
            try (InputStream is = new FileInputStream(trustStoreName)) {
                trustStore.load(is, trustStorePassword.toCharArray());
            }
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);

            return trustManagerFactory.getTrustManagers();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create trust store", e);
        }
    }

    public static class TrustAllTrustManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
            // do nothing
        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
            // do nothing
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

}
