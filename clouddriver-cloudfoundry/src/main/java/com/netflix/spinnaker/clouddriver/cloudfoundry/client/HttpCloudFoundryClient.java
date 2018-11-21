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
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.Token;
import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Response;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import okio.Buffer;
import okio.BufferedSource;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.client.OkClient;
import retrofit.converter.JacksonConverter;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class HttpCloudFoundryClient implements CloudFoundryClient {
  private final String apiHost;
  private final String user;
  private final String password;
  private final OkHttpClient okHttpClient;

  private AuthenticationService uaaService;
  private AtomicLong tokenExpirationNs = new AtomicLong(System.nanoTime());
  private volatile Token token;

  private JacksonConverter jacksonConverter;

  private Spaces spaces;
  private Organizations organizations;
  private Domains domains;
  private Routes routes;
  private Applications applications;
  private ServiceInstances serviceInstances;

  private final RequestInterceptor oauthInterceptor = new RequestInterceptor() {
    @Override
    public void intercept(RequestFacade request) {
      refreshTokenIfNecessary();
      request.addHeader("Authorization", "bearer " + token.getAccessToken());
    }
  };

  private static class RetryableApiException extends RuntimeException {
  }

  Response createRetryInterceptor(Interceptor.Chain chain) {
    Retry retry = Retry.of("cf.api.call", RetryConfig.custom()
      .retryExceptions(RetryableApiException.class)
      .build());

    AtomicReference<Response> lastResponse = new AtomicReference<>();
    try {
      return retry.executeCallable(() -> {
        Response response = chain.proceed(chain.request());
        lastResponse.set(response);

        switch (response.code()) {
          case 401:
            BufferedSource source = response.body().source();
            source.request(Long.MAX_VALUE); // request the entire body
            Buffer buffer = source.buffer();
            String body = buffer.clone().readString(Charset.forName("UTF-8"));
            if (!body.contains("Bad credentials")) {
              refreshToken();
              response = chain.proceed(chain.request().newBuilder().header("Authorization", "bearer " + token.getAccessToken()).build());
              lastResponse.set(response);
            }
            break;
          case 502:
          case 503:
          case 504:
            // after retries fail, the response body for these status codes will get wrapped up into a CloudFoundryApiException
            throw new RetryableApiException();
        }

        return response;
      });
    } catch (Exception e) {
      return lastResponse.get();
    }
  }

  public HttpCloudFoundryClient(String account, String appsManagerUri, String apiHost, String user, String password) {
    this.apiHost = apiHost;
    this.user = user;
    this.password = password;

    this.okHttpClient = new OkHttpClient();
    okHttpClient.interceptors().add(this::createRetryInterceptor);

    okHttpClient.setHostnameVerifier((s, sslSession) -> true);

    TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
      @Override
      public void checkClientTrusted(X509Certificate[] x509Certificates, String s) {
      }

      @Override
      public void checkServerTrusted(X509Certificate[] x509Certificates, String s) {
      }

      @Override
      public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
      }
    }};

    ObjectMapper mapper = new ObjectMapper();
    mapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    mapper.registerModule(new JavaTimeModule());

    this.jacksonConverter = new JacksonConverter(mapper);

    SSLContext sslContext;
    try {
      sslContext = SSLContext.getInstance("SSL");
      sslContext.init(null, trustAllCerts, new SecureRandom());
    } catch (KeyManagementException | NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }

    okHttpClient.setSslSocketFactory(sslContext.getSocketFactory());

    this.uaaService = new RestAdapter.Builder()
      .setEndpoint("https://" + apiHost.replaceAll("^api\\.", "login."))
      .setClient(new OkClient(okHttpClient)).setConverter(jacksonConverter)
      .build()
      .create(AuthenticationService.class);

    this.organizations = new Organizations(createService(OrganizationService.class));
    this.spaces = new Spaces(createService(SpaceService.class), organizations);
    this.applications = new Applications(account, appsManagerUri, createService(ApplicationService.class), spaces);
    this.domains = new Domains(createService(DomainService.class), organizations);
    this.serviceInstances = new ServiceInstances(createService(ServiceInstanceService.class), organizations, spaces);
    this.routes = new Routes(account, createService(RouteService.class), applications, domains, spaces);
  }

  private void refreshTokenIfNecessary() {
    long currentExpiration = tokenExpirationNs.get();
    long now = System.nanoTime();
    long comp = Math.min(currentExpiration, now);
    if (tokenExpirationNs.compareAndSet(comp, now)) {
      this.refreshToken();
    }
  }

  private void refreshToken() {
    token = uaaService.passwordToken("password", user, password, "cf", "");
    tokenExpirationNs.addAndGet(Duration.ofSeconds(token.getExpiresIn()).toNanos());
  }

  private <S> S createService(Class<S> serviceClass) {
    return new RestAdapter.Builder()
      .setEndpoint("https://" + apiHost)
      .setClient(new OkClient(okHttpClient))
      .setConverter(jacksonConverter)
      .setRequestInterceptor(oauthInterceptor)
      .build()
      .create(serviceClass);
  }

  @Override
  public Spaces getSpaces() {
    return spaces;
  }

  @Override
  public Organizations getOrganizations() {
    return organizations;
  }

  @Override
  public Domains getDomains() {
    return domains;
  }

  @Override
  public Routes getRoutes() {
    return routes;
  }

  @Override
  public Applications getApplications() {
    return applications;
  }

  @Override
  public ServiceInstances getServiceInstances() {
    return serviceInstances;
  }
}
