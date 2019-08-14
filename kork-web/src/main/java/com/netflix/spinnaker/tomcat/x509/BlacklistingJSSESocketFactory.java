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

import com.netflix.spectator.api.Registry;
import java.util.Objects;
import java.util.Optional;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.apache.tomcat.util.net.jsse.JSSEUtil;

public class BlacklistingJSSESocketFactory extends JSSEUtil {
  private static final String BLACKLIST_PREFIX = "blacklist:";

  private final Blacklist blacklist;
  private final Registry registry;

  public BlacklistingJSSESocketFactory(SSLHostConfigCertificate certificate, Registry registry) {
    super(certificate);
    this.registry = Objects.requireNonNull(registry);
    String blacklistFile =
        Optional.ofNullable(certificate.getSSLHostConfig().getCertificateRevocationListFile())
            .filter(file -> file.startsWith(BLACKLIST_PREFIX))
            .map(file -> file.substring(BLACKLIST_PREFIX.length()))
            .orElse(null);

    if (blacklistFile != null) {
      certificate.getSSLHostConfig().setCertificateRevocationListFile(null);
      blacklist = Blacklist.forFile(blacklistFile);
    } else {
      blacklist = null;
    }
  }

  @Override
  public TrustManager[] getTrustManagers() throws Exception {
    TrustManager[] trustManagers = super.getTrustManagers();
    if (blacklist != null && trustManagers != null) {
      int delegatedCount = 0;
      for (int i = 0; i < trustManagers.length; i++) {
        TrustManager tm = trustManagers[i];
        if (tm instanceof X509TrustManager) {
          trustManagers[i] =
              new BlacklistingX509TrustManager((X509TrustManager) tm, blacklist, registry);
          delegatedCount++;
        }
      }

      if (delegatedCount != 1) {
        throw new IllegalStateException("expected single X509TrustManager");
      }
    }

    return trustManagers;
  }
}
