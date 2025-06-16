/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.halyard.config.validate.v1.providers.cloudfoundry;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryApiException;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.AuthenticationService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.SpaceService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.Token;
import com.netflix.spinnaker.halyard.config.model.v1.node.Validator;
import com.netflix.spinnaker.halyard.config.model.v1.providers.cloudfoundry.CloudFoundryAccount;
import com.netflix.spinnaker.halyard.config.model.v1.util.PropertyUtils;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.kork.retrofit.util.RetrofitUtils;
import com.netflix.spinnaker.okhttp.Retrofit2EncodeCorrectionInterceptor;
import java.io.IOException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Data
@EqualsAndHashCode(callSuper = false)
@Slf4j
public class CloudFoundryAccountValidator extends Validator<CloudFoundryAccount> {

  private JacksonConverterFactory jacksonConverterFactory = createJacksonConverterFactory();
  private X509TrustManager x509TrustManager;
  private OkHttpClient.Builder secureOkClientBuilder = createHttpClientBuilder(false);
  private OkHttpClient.Builder insecureOkClientBuilder = createHttpClientBuilder(true);

  @Override
  public void validate(
      ConfigProblemSetBuilder problemSetBuilder, CloudFoundryAccount cloudFoundryAccount) {
    String accountName = cloudFoundryAccount.getName();

    DaemonTaskHandler.message(
        "Validating "
            + accountName
            + " with "
            + CloudFoundryAccountValidator.class.getSimpleName());

    String apiHost = cloudFoundryAccount.getApiHost();
    URL appsManagerUrl = cloudFoundryAccount.getAppsManagerUrl();
    URL metricsUrl = cloudFoundryAccount.getMetricsUrl();
    String password = cloudFoundryAccount.getPassword();
    String user = cloudFoundryAccount.getUser();
    boolean skipSslValidation = cloudFoundryAccount.isSkipSslValidation();

    if (StringUtils.isEmpty(user) || StringUtils.isEmpty(password)) {
      problemSetBuilder.addProblem(
          Problem.Severity.ERROR, "You must provide a user and a password");
    }

    if (StringUtils.isEmpty(apiHost)) {
      problemSetBuilder.addProblem(
          Problem.Severity.ERROR, "You must provide a CF API endpoint host");
    }

    if (appsManagerUrl == null) {
      problemSetBuilder.addProblem(
          Problem.Severity.WARNING,
          "To be able to link server groups to CF Apps Manager a URI is required");
    } else if (!isHttp(appsManagerUrl.getProtocol())) {
      problemSetBuilder.addProblem(
          Severity.ERROR,
          "Apps manager URL scheme must be HTTP or HTTPS for account: " + accountName);
    }

    if (metricsUrl == null) {
      problemSetBuilder.addProblem(
          Problem.Severity.WARNING,
          "To be able to link server groups to CF Metrics a URL is required");
    } else if (!isHttp(metricsUrl.getProtocol())) {
      problemSetBuilder.addProblem(
          Severity.ERROR, "Metrics URL scheme must be HTTP or HTTPS for account: " + accountName);
    }

    if (skipSslValidation) {
      problemSetBuilder.addProblem(
          Problem.Severity.WARNING,
          "SKIPPING SSL server certificate validation of the Cloud Foundry API endpoint");
    }

    if (PropertyUtils.anyContainPlaceholder(apiHost, user, password)) {
      problemSetBuilder.addProblem(
          Problem.Severity.WARNING,
          "Skipping connection validation because one or more credential contains a placeholder value");
      return;
    }

    try {
      SpaceService spaceService = createSpaceService(apiHost, skipSslValidation, user, password);
      int count = spaceService.all(null, null, null).execute().body().getResources().size();
      log.info("Retrieved {} spaces using account {}", count, accountName);
    } catch (Exception e) {
      problemSetBuilder.addProblem(
          Problem.Severity.ERROR,
          "Failed to fetch spaces while validating account '"
              + accountName
              + "': "
              + e.getMessage()
              + ".");
    }
  }

  private SpaceService createSpaceService(
      String apiHost, boolean skipSslValidation, String user, String password) {
    OkHttpClient.Builder okClientBuilder =
        skipSslValidation ? insecureOkClientBuilder : secureOkClientBuilder;
    AuthInterceptor authInterceptor =
        new AuthInterceptor(getToken(apiHost, skipSslValidation, user, password).getAccessToken());
    okClientBuilder.addInterceptor(authInterceptor);
    return new Retrofit.Builder()
        .baseUrl(RetrofitUtils.getBaseUrl("https://" + apiHost))
        .client(okClientBuilder.build())
        .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
        .addConverterFactory(jacksonConverterFactory)
        .build()
        .create(SpaceService.class);
  }

  private Token getToken(String apiHost, boolean skipSslValidation, String user, String password) {
    String baseUrl = RetrofitUtils.getBaseUrl("https://" + apiHost.replaceAll("^api\\.", "login."));
    OkHttpClient.Builder okClientBuilder =
        skipSslValidation ? insecureOkClientBuilder : secureOkClientBuilder;
    AuthenticationService uaaService =
        new Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okClientBuilder.build())
            .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
            .addConverterFactory(jacksonConverterFactory)
            .build()
            .create(AuthenticationService.class);
    Token token = null;
    try {
      token = uaaService.passwordToken("password", user, password, "cf", "").execute().body();
    } catch (Exception e) {
      throw new CloudFoundryApiException(e);
    }
    return token;
  }

  @NotNull
  private JacksonConverterFactory createJacksonConverterFactory() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    mapper.registerModule(new JavaTimeModule());
    return JacksonConverterFactory.create(mapper);
  }

  private OkHttpClient.Builder createHttpClientBuilder(boolean skipSslValidation) {
    OkHttpClient.Builder oBuilder = new OkHttpClient.Builder();
    oBuilder.addInterceptor(new Retrofit2EncodeCorrectionInterceptor());

    if (skipSslValidation) {
      oBuilder.hostnameVerifier((s, sslSession) -> true);

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

      oBuilder.sslSocketFactory(sslContext.getSocketFactory(), x509TrustManager);
    }

    return oBuilder;
  }

  private boolean isHttp(String protocol) {
    return "http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol);
  }

  public static class AuthInterceptor implements Interceptor {
    private final String token;

    public AuthInterceptor(String token) {
      this.token = token;
    }

    @Override
    public @NotNull Response intercept(Chain chain) throws IOException {
      Request original = chain.request();
      Request.Builder builder =
          original
              .newBuilder()
              .header("Authorization", "Bearer " + token)
              .method(original.method(), original.body());
      return chain.proceed(builder.build());
    }
  }
}
