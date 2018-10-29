/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.clouddriver.docker.registry.api.v2.client;

import com.netflix.spinnaker.clouddriver.docker.registry.security.TrustAllX509TrustManager;
import com.squareup.okhttp.OkHttpClient;
import retrofit.client.OkClient;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

public class DefaultDockerOkClientProvider implements DockerOkClientProvider {

  @Override
  public OkClient provide(String address, long timeoutMs, boolean insecure) {
    OkHttpClient client = new OkHttpClient();
    client.setReadTimeout(timeoutMs, TimeUnit.MILLISECONDS);

    if (insecure) {
      SSLContext sslContext;
      try {
        sslContext = SSLContext.getInstance("SSL");
        TrustManager[] trustManagers = {new TrustAllX509TrustManager()};
        sslContext.init(null, trustManagers, new SecureRandom());
      } catch (NoSuchAlgorithmException|KeyManagementException e) {
        throw new IllegalStateException("Failed configuring insecure SslSocketFactory", e);
      }
      client.setSslSocketFactory(sslContext.getSocketFactory());
    }

    return new OkClient(client);
  }
}
