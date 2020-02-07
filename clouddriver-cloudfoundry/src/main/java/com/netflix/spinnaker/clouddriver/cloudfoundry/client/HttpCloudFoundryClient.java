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
import io.github.resilience4j.retry.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import lombok.extern.slf4j.Slf4j;
import okio.Buffer;
import okio.BufferedSource;
import org.apache.commons.fileupload.MultipartStream;
import org.cloudfoundry.dropsonde.events.EventFactory.Envelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.client.OkClient;
import retrofit.converter.ConversionException;
import retrofit.converter.Converter;
import retrofit.converter.JacksonConverter;
import retrofit.mime.TypedInput;
import retrofit.mime.TypedOutput;

/**
 * Waiting for this issue to be resolved before replacing this class by the CF Java Client:
 * https://github.com/cloudfoundry/cf-java-client/issues/938
 */
@Slf4j
public class HttpCloudFoundryClient implements CloudFoundryClient {
  private final String apiHost;
  private final String user;
  private final String password;
  private final OkHttpClient okHttpClient;
  private Logger logger = LoggerFactory.getLogger(HttpCloudFoundryClient.class);

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
  private ServiceKeys serviceKeys;
  private Tasks tasks;
  private Logs logs;

  private final RequestInterceptor oauthInterceptor =
      new RequestInterceptor() {
        @Override
        public void intercept(RequestFacade request) {
          refreshTokenIfNecessary();
          request.addHeader("Authorization", "bearer " + token.getAccessToken());
        }
      };

  private static class RetryableApiException extends RuntimeException {
    RetryableApiException(String message) {
      super(message);
    }

    RetryableApiException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  Response createRetryInterceptor(Interceptor.Chain chain) {
    final String callName = "cf.api.call";
    Retry retry =
        Retry.of(
            callName,
            RetryConfig.custom()
                .intervalFunction(IntervalFunction.ofExponentialBackoff(Duration.ofSeconds(10), 3))
                .retryExceptions(RetryableApiException.class)
                .build());
    logger.debug("cf request: " + chain.request().urlString());
    AtomicReference<Response> lastResponse = new AtomicReference<>();
    try {
      return retry.executeCallable(
          () -> {
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
                  response =
                      chain.proceed(
                          chain
                              .request()
                              .newBuilder()
                              .header("Authorization", "bearer " + token.getAccessToken())
                              .build());
                  lastResponse.set(response);
                }
                break;
              case 502:
              case 503:
              case 504:
                // after retries fail, the response body for these status codes will get wrapped up
                // into a CloudFoundryApiException
                throw new RetryableApiException(
                    "Response Code "
                        + response.code()
                        + ": "
                        + chain.request().httpUrl()
                        + " attempting retry");
            }

            return response;
          });
    } catch (SocketTimeoutException e) {
      throw new RetryableApiException(
          "Timeout " + callName + " " + chain.request().httpUrl() + ",  attempting retry", e);
    } catch (Exception e) {
      final Response response = lastResponse.get();
      if (response == null) {
        throw new IllegalStateException(e);
      }
      return response;
    }
  }

  public HttpCloudFoundryClient(
      String account,
      String appsManagerUri,
      String metricsUri,
      String apiHost,
      String user,
      String password,
      boolean skipSslValidation,
      Integer resultsPerPage,
      int maxCapiConnectionsForCache) {
    this.apiHost = apiHost;
    this.user = user;
    this.password = password;

    this.okHttpClient = createHttpClient(skipSslValidation);

    okHttpClient.interceptors().add(this::createRetryInterceptor);

    ObjectMapper mapper = new ObjectMapper();
    mapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    mapper.registerModule(new JavaTimeModule());

    this.jacksonConverter = new JacksonConverter(mapper);

    this.uaaService =
        new RestAdapter.Builder()
            .setEndpoint("https://" + apiHost.replaceAll("^api\\.", "login."))
            .setClient(new OkClient(okHttpClient))
            .setConverter(jacksonConverter)
            .build()
            .create(AuthenticationService.class);

    this.organizations = new Organizations(createService(OrganizationService.class));
    this.spaces = new Spaces(createService(SpaceService.class), organizations);
    this.applications =
        new Applications(
            account,
            appsManagerUri,
            metricsUri,
            createService(ApplicationService.class),
            spaces,
            resultsPerPage,
            maxCapiConnectionsForCache);
    this.domains = new Domains(createService(DomainService.class), organizations);
    this.serviceInstances =
        new ServiceInstances(
            createService(ServiceInstanceService.class),
            createService(ConfigService.class),
            organizations,
            spaces);
    this.routes =
        new Routes(
            account,
            createService(RouteService.class),
            applications,
            domains,
            spaces,
            resultsPerPage,
            maxCapiConnectionsForCache);
    this.serviceKeys = new ServiceKeys(createService(ServiceKeyService.class), spaces);
    this.tasks = new Tasks(createService(TaskService.class));

    this.logs =
        new Logs(
            new RestAdapter.Builder()
                .setEndpoint("https://" + apiHost.replaceAll("^api\\.", "doppler."))
                .setClient(new OkClient(okHttpClient))
                .setConverter(new ProtobufDopplerEnvelopeConverter())
                .setRequestInterceptor(oauthInterceptor)
                .build()
                .create(DopplerService.class));
  }

  private static OkHttpClient createHttpClient(boolean skipSslValidation) {
    OkHttpClient client = new OkHttpClient();

    if (skipSslValidation) {
      client.setHostnameVerifier((s, sslSession) -> true);

      TrustManager[] trustAllCerts =
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
        sslContext.init(null, trustAllCerts, new SecureRandom());
      } catch (KeyManagementException | NoSuchAlgorithmException e) {
        throw new RuntimeException(e);
      }

      client.setSslSocketFactory(sslContext.getSocketFactory());
    }

    return client;
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
    try {
      token = uaaService.passwordToken("password", user, password, "cf", "");
    } catch (Exception e) {
      log.warn("Failed to obtain a token", e);
      throw e;
    }
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

  @Override
  public ServiceKeys getServiceKeys() {
    return serviceKeys;
  }

  @Override
  public Tasks getTasks() {
    return tasks;
  }

  @Override
  public Logs getLogs() {
    return logs;
  }

  static class ProtobufDopplerEnvelopeConverter implements Converter {
    @Override
    public Object fromBody(TypedInput body, Type type) throws ConversionException {
      try {
        byte[] boundaryBytes = extractMultipartBoundary(body.mimeType()).getBytes();
        MultipartStream multipartStream = new MultipartStream(body.in(), boundaryBytes, 4096, null);

        List<Envelope> envelopes = new ArrayList<>();
        ByteArrayOutputStream os = new ByteArrayOutputStream(4096);

        boolean nextPart = multipartStream.skipPreamble();
        while (nextPart) {
          // Skipping the empty part headers
          multipartStream.readByte(); // 0x0D
          multipartStream.readByte(); // 0x0A

          os.reset();
          multipartStream.readBodyData(os);
          envelopes.add(Envelope.parseFrom(os.toByteArray()));

          nextPart = multipartStream.readBoundary();
        }

        return envelopes;
      } catch (IOException e) {
        throw new ConversionException(e);
      }
    }

    @Override
    public TypedOutput toBody(Object object) {
      throw new UnsupportedOperationException("Deserializer only");
    }

    private static Pattern BOUNDARY_PATTERN = Pattern.compile("multipart/.+; boundary=(.*)");

    private static String extractMultipartBoundary(String contentType) {
      Matcher matcher = BOUNDARY_PATTERN.matcher(contentType);
      if (matcher.matches()) {
        return matcher.group(1);
      } else {
        throw new IllegalStateException(
            String.format(
                "Content-Type %s does not contain a valid multipart boundary", contentType));
      }
    }
  }
}
