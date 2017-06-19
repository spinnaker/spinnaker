/*
 * Copyright 2017 Armory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.events

import com.squareup.okhttp.OkHttpClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.client.Client
import retrofit.client.OkClient

import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager


interface OkHttpClientFactory {
  OkHttpClient getInsecureClient()
}

@Component
class OkHttpClientFactoryImpl implements OkHttpClientFactory {

  OkHttpClient getInsecureClient() {
    // Create a trust manager that does not validate certificate chains
    def trustAllCerts = [
      checkClientTrusted: { chain, authType -> },
      checkServerTrusted: { chain, authType -> },
      getAcceptedIssuers: { null }
    ]

    def nullHostnameVerifier = [
      verify: { hostname, session -> true }
    ]

    SSLContext sc = SSLContext.getInstance("SSL")
    sc.init(null, [trustAllCerts as X509TrustManager] as TrustManager[], null)

    SSLSocketFactory sslSocketFactory = sc.getSocketFactory()
    OkHttpClient okHttpClient = new OkHttpClient()
    okHttpClient.setSslSocketFactory(sslSocketFactory)
    okHttpClient.setHostnameVerifier(nullHostnameVerifier as HostnameVerifier)
    return okHttpClient
  }
}

@Component
class RestClientFactory {

  @Autowired
  OkHttpClientFactory httpClientFactory

  Client getClient(Boolean insecure) {
    if (insecure) {
      return new OkClient(httpClientFactory.getInsecureClient())
    } else {
      return new OkClient()
    }
  }
}
