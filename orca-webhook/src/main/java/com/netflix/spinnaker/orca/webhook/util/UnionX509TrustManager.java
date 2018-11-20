/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.orca.webhook.util;

import javax.net.ssl.X509TrustManager;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

// X509TrustManager that represents the union of multiple X509TrustManagers; a trust check succeeds if it succeeds on
// any of the contained trust managers
public class UnionX509TrustManager implements X509TrustManager {
  private final List<X509TrustManager> delegates;

  public UnionX509TrustManager(List<X509TrustManager> delegates) {
    this.delegates = Collections.unmodifiableList(delegates);
  }

  public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
    for (X509TrustManager delegate : delegates) {
      try {
        delegate.checkClientTrusted(chain, authType);
        return;
      } catch (CertificateException ignored) { }
    }
    throw new CertificateException("None of the configured trust managers trusted the specified client.");
  }

  public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
    for (X509TrustManager delegate : delegates) {
      try {
        delegate.checkServerTrusted(chain, authType);
        return;
      } catch (CertificateException ignored) { }
    }
    throw new CertificateException("None of the configured trust managers trusted the specified server.");
  }

  public X509Certificate[] getAcceptedIssuers() {
    ArrayList<X509Certificate> certificates = new ArrayList<>();
    for (X509TrustManager delegate : delegates) {
      certificates.addAll(Arrays.asList(delegate.getAcceptedIssuers()));
    }
    return certificates.toArray(new X509Certificate[0]);
  }
}
