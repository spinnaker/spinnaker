/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.docker.registry.security

import java.security.cert.CertificateException
import java.security.cert.X509Certificate

import javax.net.ssl.X509TrustManager

class TrustAllX509TrustManager implements X509TrustManager {

  @Override
  void checkServerTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {
  }

  @Override
  void checkClientTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {
  }

  @Override
  X509Certificate[] getAcceptedIssuers() {
    X509Certificate[] result = new X509Certificate[0];
    return result;
  }

}
