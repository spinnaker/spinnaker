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

package com.netflix.spinnaker.kork.tomcat.x509;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import java.security.cert.CRLReason;
import java.security.cert.CertificateException;
import java.security.cert.CertificateRevokedException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.X509TrustManager;

public class BlocklistingX509TrustManager implements X509TrustManager {
  // Hookpoint for shutoff via property monitor:
  public static AtomicBoolean BLOCKLIST_ENABLED = new AtomicBoolean(true);
  private final X509TrustManager delegate;
  private final Blocklist blocklist;
  private final Registry registry;
  private final Id checkClientTrusted;

  public BlocklistingX509TrustManager(
      X509TrustManager delegate, Blocklist blocklist, Registry registry) {
    this.delegate = Objects.requireNonNull(delegate);
    this.blocklist = Objects.requireNonNull(blocklist);
    this.registry = Objects.requireNonNull(registry);
    checkClientTrusted = registry.createId("ssl.blocklist.checkClientTrusted");
  }

  @Override
  public void checkClientTrusted(X509Certificate[] x509Certificates, String authType)
      throws CertificateException {
    if (BLOCKLIST_ENABLED.get()) {
      boolean rejected = false;
      try {
        if (x509Certificates != null) {
          for (X509Certificate cert : x509Certificates) {
            if (blocklist.isBlocklisted(cert)) {
              rejected = true;
              throw new CertificateRevokedException(
                  new Date(),
                  CRLReason.UNSPECIFIED,
                  cert.getIssuerX500Principal(),
                  Collections.emptyMap());
            }
          }
        }
      } finally {
        registry
            .counter(checkClientTrusted.withTag("rejected", Boolean.toString(rejected)))
            .increment();
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
