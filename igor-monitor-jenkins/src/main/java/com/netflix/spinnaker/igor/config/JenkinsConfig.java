package com.netflix.spinnaker.igor.config;

import static java.lang.String.format;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;
import com.google.common.base.Strings;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.igor.config.client.DefaultJenkinsOkHttpClientProvider;
import com.netflix.spinnaker.igor.config.client.DefaultJenkinsRetrofitRequestInterceptorProvider;
import com.netflix.spinnaker.igor.config.client.JenkinsOkHttpClientProvider;
import com.netflix.spinnaker.igor.config.client.JenkinsRetrofitRequestInterceptorProvider;
import com.netflix.spinnaker.igor.jenkins.JenkinsService;
import com.netflix.spinnaker.igor.jenkins.client.JenkinsClient;
import com.netflix.spinnaker.igor.service.BuildServices;
import com.netflix.spinnaker.kork.telemetry.InstrumentedProxy;
import com.squareup.okhttp.OkHttpClient;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.net.ssl.*;
import javax.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit.Endpoints;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.client.OkClient;
import retrofit.converter.JacksonConverter;

/**
 * Converts the list of Jenkins Configuration properties a collection of clients to access the
 * Jenkins hosts
 */
@Configuration
@Slf4j
@ConditionalOnProperty("jenkins.enabled")
@EnableConfigurationProperties(JenkinsProperties.class)
public class JenkinsConfig {
  public static JenkinsService jenkinsService(
      String jenkinsHostId, JenkinsClient jenkinsClient, Boolean csrf, Permissions permissions) {
    return new JenkinsService(jenkinsHostId, jenkinsClient, csrf, permissions);
  }

  public static ObjectMapper getObjectMapper() {
    return new XmlMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .registerModule(new JaxbAnnotationModule());
  }

  public static JenkinsClient jenkinsClient(
      JenkinsProperties.JenkinsHost host,
      OkHttpClient client,
      RequestInterceptor requestInterceptor,
      int timeout) {
    try {
      return checkedJenkinsClient(host, client, requestInterceptor, timeout);
    } catch (GeneralSecurityException | IOException e) {
      throw new RuntimeException(
          format("Cannot configure jenkins client for host '%s'", host.getName()), e);
    }
  }

  public static JenkinsClient checkedJenkinsClient(
      JenkinsProperties.JenkinsHost host,
      OkHttpClient client,
      RequestInterceptor requestInterceptor,
      int timeout)
      throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException, IOException,
          CertificateException, UnrecoverableKeyException {
    client.setReadTimeout(timeout, TimeUnit.MILLISECONDS);

    if (host.getSkipHostnameVerification()) {
      client.setHostnameVerifier((hostname, session) -> true);
    }

    TrustManager[] trustManagers = null;
    KeyManager[] keyManagers = null;

    if (!Strings.isNullOrEmpty(host.getTrustStore())) {
      if (host.getTrustStore().equals("*")) {
        trustManagers =
            new ArrayList<>(Collections.singletonList(new TrustAllTrustManager()))
                .toArray(new TrustManager[0]);
      } else {
        String trustStorePassword = host.getTrustStorePassword();

        KeyStore trustStore = KeyStore.getInstance(host.getTrustStoreType());
        trustStore.load(
            new ByteArrayInputStream(Files.readAllBytes(Paths.get(host.getTrustStore()))),
            trustStorePassword.toCharArray());

        TrustManagerFactory trustManagerFactory =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);

        trustManagers = trustManagerFactory.getTrustManagers();
      }
    }

    if (!Strings.isNullOrEmpty(host.getKeyStore())) {
      KeyStore keyStore = KeyStore.getInstance(host.getKeyStoreType());

      keyStore.load(
          new ByteArrayInputStream(Files.readAllBytes(Paths.get(host.getKeyStore()))),
          host.getKeyStorePassword().toCharArray());

      KeyManagerFactory keyManagerFactory =
          KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      keyManagerFactory.init(keyStore, host.getKeyStorePassword().toCharArray());

      keyManagers = keyManagerFactory.getKeyManagers();
    }

    if (trustManagers != null || keyManagers != null) {
      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(keyManagers, trustManagers, null);

      client.setSslSocketFactory(sslContext.getSocketFactory());
    }

    return new RestAdapter.Builder()
        .setEndpoint(Endpoints.newFixedEndpoint(host.getAddress()))
        .setRequestInterceptor(
            request -> {
              request.addHeader("User-Agent", "Spinnaker-igor");
              requestInterceptor.intercept(request);
            })
        .setClient(new OkClient(client))
        .setConverter(new JacksonConverter(getObjectMapper()))
        .build()
        .create(JenkinsClient.class);
  }

  public static JenkinsClient jenkinsClient(
      JenkinsProperties.JenkinsHost host,
      OkHttpClient client,
      RequestInterceptor requestInterceptor) {
    return JenkinsConfig.jenkinsClient(host, client, requestInterceptor, 30000);
  }

  public static JenkinsClient jenkinsClient(JenkinsProperties.JenkinsHost host, int timeout) {
    OkHttpClient client = new OkHttpClient();
    return jenkinsClient(host, client, RequestInterceptor.NONE, timeout);
  }

  public static JenkinsClient jenkinsClient(JenkinsProperties.JenkinsHost host) {
    return JenkinsConfig.jenkinsClient(host, 30000);
  }

  @Bean
  @ConditionalOnMissingBean
  public JenkinsOkHttpClientProvider jenkinsOkHttpClientProvider() {
    return new DefaultJenkinsOkHttpClientProvider();
  }

  @Bean
  @ConditionalOnMissingBean
  public JenkinsRetrofitRequestInterceptorProvider jenkinsRetrofitRequestInterceptorProvider() {
    return new DefaultJenkinsRetrofitRequestInterceptorProvider();
  }

  @Bean
  public Map<String, JenkinsService> jenkinsMasters(
      BuildServices buildServices,
      final IgorConfigurationProperties igorConfigurationProperties,
      @Valid JenkinsProperties jenkinsProperties,
      final JenkinsOkHttpClientProvider jenkinsOkHttpClientProvider,
      final JenkinsRetrofitRequestInterceptorProvider jenkinsRetrofitRequestInterceptorProvider,
      final Registry registry) {
    log.info("creating jenkinsMasters");

    Map<String, JenkinsService> jenkinsMasters =
        jenkinsProperties.getMasters().stream()
            .filter(Objects::nonNull)
            .collect(
                Collectors.toMap(
                    (JenkinsProperties.JenkinsHost host) -> {
                      log.info("bootstrapping {} as {}", host.getAddress(), host.getName());
                      return host.getName();
                    },
                    (JenkinsProperties.JenkinsHost host) -> {
                      JenkinsClient client =
                          InstrumentedProxy.proxy(
                              registry,
                              jenkinsClient(
                                  host,
                                  jenkinsOkHttpClientProvider.provide(host),
                                  jenkinsRetrofitRequestInterceptorProvider.provide(host),
                                  igorConfigurationProperties.getClient().getTimeout()),
                              "jenkinsClient",
                              Collections.singletonMap("master", host.getName()));
                      return jenkinsService(
                          host.getName(), client, host.getCsrf(), host.getPermissions().build());
                    }));

    buildServices.addServices(jenkinsMasters);
    return jenkinsMasters;
  }

  public static class TrustAllTrustManager implements X509TrustManager {
    @Override
    public void checkClientTrusted(X509Certificate[] x509Certificates, String s)
        throws CertificateException {
      // do nothing
    }

    @Override
    public void checkServerTrusted(X509Certificate[] x509Certificates, String s)
        throws CertificateException {
      // do nothing
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
      return new X509Certificate[0];
    }
  }
}
