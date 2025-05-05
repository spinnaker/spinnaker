package com.netflix.spinnaker.kork.docker.service;

import com.netflix.spinnaker.kork.docker.security.TrustAllX509TrustManager;
import okhttp3.OkHttpClient;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

public class DefaultDockerOkClientProvider implements DockerOkClientProvider {

  @Override
  public OkHttpClient provide(String address, long timeoutMs, boolean insecure) {
    OkHttpClient.Builder clientBuilder =
      new OkHttpClient.Builder().readTimeout(timeoutMs, TimeUnit.MILLISECONDS);

    if (insecure) {
      SSLContext sslContext;
      TrustManager[] trustManagers = {new TrustAllX509TrustManager()};
      try {
        sslContext = SSLContext.getInstance("SSL");
        sslContext.init(null, trustManagers, new SecureRandom());
      } catch (NoSuchAlgorithmException | KeyManagementException e) {
        throw new IllegalStateException("Failed configuring insecure SslSocketFactory", e);
      }
      clientBuilder.sslSocketFactory(
        sslContext.getSocketFactory(), (X509TrustManager) trustManagers[0]);
    }

    return clientBuilder.build();
  }
}
