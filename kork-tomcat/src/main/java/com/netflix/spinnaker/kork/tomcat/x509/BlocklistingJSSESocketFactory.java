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

import com.netflix.spectator.api.Registry;
import java.util.Objects;
import java.util.Optional;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.apache.tomcat.util.net.jsse.JSSEUtil;

public class BlocklistingJSSESocketFactory extends JSSEUtil {
  private static final String BLOCKLIST_PREFIX = "blocklist:";

  private final Blocklist blocklist;
  private final Registry registry;

  public BlocklistingJSSESocketFactory(SSLHostConfigCertificate certificate, Registry registry) {
    super(certificate);
    this.registry = Objects.requireNonNull(registry);
    String blocklistFile =
        Optional.ofNullable(certificate.getSSLHostConfig().getCertificateRevocationListFile())
            .filter(file -> file.startsWith(BLOCKLIST_PREFIX))
            .map(file -> file.substring(BLOCKLIST_PREFIX.length()))
            .orElse(null);

    if (blocklistFile != null) {
      certificate.getSSLHostConfig().setCertificateRevocationListFile(null);
      blocklist = Blocklist.forFile(blocklistFile);
    } else {
      blocklist = null;
    }
  }

  @Override
  public TrustManager[] getTrustManagers() throws Exception {
    TrustManager[] trustManagers = super.getTrustManagers();
    if (blocklist != null && trustManagers != null) {
      int delegatedCount = 0;
      for (int i = 0; i < trustManagers.length; i++) {
        TrustManager tm = trustManagers[i];
        if (tm instanceof X509TrustManager) {
          trustManagers[i] =
              new BlocklistingX509TrustManager((X509TrustManager) tm, blocklist, registry);
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
