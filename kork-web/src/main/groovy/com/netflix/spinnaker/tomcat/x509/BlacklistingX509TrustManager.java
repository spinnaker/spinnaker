/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.tomcat.x509;

import java.security.cert.CRLReason;
import java.security.cert.CertificateException;
import java.security.cert.CertificateRevokedException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Date;
import java.util.Objects;
import javax.net.ssl.X509TrustManager;

public class BlacklistingX509TrustManager implements X509TrustManager {
  private final X509TrustManager delegate;
  private final Blacklist blacklist;

  public BlacklistingX509TrustManager(X509TrustManager delegate, Blacklist blacklist) {
    this.delegate = Objects.requireNonNull(delegate);
    this.blacklist = Objects.requireNonNull(blacklist);
  }

  @Override
  public void checkClientTrusted(X509Certificate[] x509Certificates, String authType)
      throws CertificateException {
    if (x509Certificates != null) {
      for (X509Certificate cert : x509Certificates) {
        if (blacklist.isBlacklisted(cert)) {
          throw new CertificateRevokedException(
              new Date(),
              CRLReason.UNSPECIFIED,
              cert.getIssuerX500Principal(),
              Collections.emptyMap());
        }
      }
    }

    delegate.checkClientTrusted(x509Certificates, authType);
  }

  @Override
  public void checkServerTrusted(X509Certificate[] x509Certificates, String authType)
      throws CertificateException {
    delegate.checkServerTrusted(x509Certificates, authType);
  }

  @Override
  public X509Certificate[] getAcceptedIssuers() {
    return delegate.getAcceptedIssuers();
  }
}
