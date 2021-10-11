/*
 * Copyright 2018 Pivotal, Inc.
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

package com.netflix.spinnaker.clouddriver.cloudfoundry.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.*;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.retry.RetryInterceptor;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.tokens.AccessTokenAuthenticator;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.tokens.AccessTokenInterceptor;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.tokens.AccessTokenProvider;
import com.netflix.spinnaker.clouddriver.cloudfoundry.config.CloudFoundryConfigurationProperties;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.ForkJoinPool;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import retrofit2.converter.protobuf.ProtoConverterFactory;

/**
 * Waiting for this issue to be resolved before replacing this class by the CF Java Client:
 * https://github.com/cloudfoundry/cf-java-client/issues/938
 */
@Slf4j
public class HttpCloudFoundryClient implements CloudFoundryClient {
  private final String apiHost;
  private final String user;
  private final String password;
  private Logger logger = LoggerFactory.getLogger(HttpCloudFoundryClient.class);
  @Getter private AuthenticationService uaaService;
  @Getter private Spaces spaces;
  @Getter private Organizations organizations;
  @Getter private Domains domains;
  @Getter private Routes routes;
  @Getter private Applications applications;
  @Getter private ServiceInstances serviceInstances;
  @Getter private ServiceKeys serviceKeys;
  @Getter private Tasks tasks;
  @Getter private Logs logs;
  @Getter private Processes processes;

  public HttpCloudFoundryClient(
      String account,
      String appsManagerUri,
      String metricsUri,
      String apiHost,
      String user,
      String password,
      boolean useHttps,
      boolean skipSslValidation,
      boolean onlySpinnakerManaged,
      Integer resultsPerPage,
      ForkJoinPool forkJoinPool,
      OkHttpClient.Builder okHttpClientBuilder,
      CloudFoundryConfigurationProperties.ClientConfig clientConfig,
      CloudFoundryConfigurationProperties.LocalCacheConfig localCacheConfig) {

    this.apiHost = apiHost;
    this.user = user;
    this.password = password;

    ObjectMapper mapper = new ObjectMapper();
    mapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    mapper.registerModule(new JavaTimeModule());

    // The UAA service is built first because the Authenticator interceptor needs it to get tokens
    // from CF.
    OkHttpClient okHttpClient = applySslValidator(okHttpClientBuilder, skipSslValidation);
    this.uaaService =
        new Retrofit.Builder()
            .baseUrl(
                (useHttps ? "https://" : "http://") + this.apiHost.replaceAll("^api\\.", "login."))
            .client(okHttpClient)
            .addConverterFactory(JacksonConverterFactory.create(mapper))
            .build()
            .create(AuthenticationService.class);

    // The remaining services need the AccessTokenAuthenticator in order to retry for 401 responses.
    AccessTokenProvider accessTokenProvider =
        new AccessTokenProvider(user, password, this.uaaService);
    okHttpClient =
        okHttpClient
            .newBuilder()
            .authenticator(new AccessTokenAuthenticator(accessTokenProvider))
            .addInterceptor(new AccessTokenInterceptor(accessTokenProvider))
            .addInterceptor(new RetryInterceptor(clientConfig.getMaxRetries()))
            .build();

    // Shared retrofit targeting cf api with preconfigured okhttpclient and jackson converter
    Retrofit retrofit =
        new Retrofit.Builder()
            .client(okHttpClient)
            .baseUrl((useHttps ? "https://" : "http://") + this.apiHost)
            .addConverterFactory(JacksonConverterFactory.create(mapper))
            .build();

    this.organizations = new Organizations(retrofit.create(OrganizationService.class));
    this.spaces = new Spaces(retrofit.create(SpaceService.class), organizations);
    this.processes = new Processes(retrofit.create(ProcessesService.class));

    this.applications =
        new Applications(
            account,
            appsManagerUri,
            metricsUri,
            retrofit.create(ApplicationService.class),
            spaces,
            processes,
            resultsPerPage,
            onlySpinnakerManaged,
            forkJoinPool,
            localCacheConfig);
    this.domains = new Domains(retrofit.create(DomainService.class), organizations);
    this.serviceInstances =
        new ServiceInstances(
            retrofit.create(ServiceInstanceService.class),
            retrofit.create(ConfigService.class),
            spaces);
    this.routes =
        new Routes(
            account,
            retrofit.create(RouteService.class),
            applications,
            domains,
            spaces,
            resultsPerPage,
            forkJoinPool,
            localCacheConfig);
    this.serviceKeys = new ServiceKeys(retrofit.create(ServiceKeyService.class), spaces);
    this.tasks = new Tasks(retrofit.create(TaskService.class));

    // Logs requires retrofit with different baseUrl and converterFactory
    this.logs =
        new Logs(
            new Retrofit.Builder()
                .client(okHttpClient)
                .baseUrl(
                    (useHttps ? "https://" : "http://") + apiHost.replaceAll("^api\\.", "doppler."))
                .addConverterFactory(ProtoConverterFactory.create())
                .build()
                .create(DopplerService.class));
  }

  private static OkHttpClient applySslValidator(
      OkHttpClient.Builder builder, boolean skipSslValidation) {
    if (skipSslValidation) {
      builder.hostnameVerifier((s, sslSession) -> true);

      TrustManager[] trustManagers =
          new TrustManager[] {
            new X509TrustManager() {
              @Override
              public void checkClientTrusted(X509Certificate[] x509Certificates, String s) {}

              @Override
              public void checkServerTrusted(X509Certificate[] x509Certificates, String s) {}

              @Override
              public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
              }
            }
          };

      SSLContext sslContext;
      try {
        sslContext = SSLContext.getInstance("SSL");
        sslContext.init(null, trustManagers, new SecureRandom());
      } catch (KeyManagementException | NoSuchAlgorithmException e) {
        throw new RuntimeException(e);
      }

      X509TrustManager trustManager = (X509TrustManager) trustManagers[0];
      builder.sslSocketFactory(sslContext.getSocketFactory(), trustManager);
    }
    return builder.build();
  }
}
