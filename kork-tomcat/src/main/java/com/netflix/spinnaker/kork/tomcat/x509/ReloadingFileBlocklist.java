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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.math.BigInteger;
import java.nio.file.Files;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.security.auth.x500.X500Principal;

public class ReloadingFileBlocklist implements Blocklist {
  private static class Entry {
    private static final String DELIMITER = ":::";
    private final X500Principal issuer;
    private final BigInteger serial;

    private static Entry fromString(String blocklistEntry) {
      int idx = blocklistEntry.indexOf(DELIMITER);
      if (idx == -1) {
        throw new IllegalArgumentException(
            "Missing delimiter " + DELIMITER + " in " + blocklistEntry);
      }
      X500Principal principal = new X500Principal(blocklistEntry.substring(0, idx));
      BigInteger serial = new BigInteger(blocklistEntry.substring(idx + DELIMITER.length()));
      return new Entry(principal, serial);
    }

    private Entry(X500Principal issuer, BigInteger serial) {
      this.issuer = Objects.requireNonNull(issuer);
      this.serial = Objects.requireNonNull(serial);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Entry entry = (Entry) o;

      if (!issuer.equals(entry.issuer)) return false;
      return serial.equals(entry.serial);
    }

    @Override
    public int hashCode() {
      int result = issuer.hashCode();
      result = 31 * result + serial.hashCode();
      return result;
    }
  }

  private static final int DEFAULT_RELOAD_INTERVAL_SECONDS = 5;
  private final String blocklistFile;
  private final LoadingCache<String, Set<Entry>> blocklist;

  public ReloadingFileBlocklist(String blocklistFile, long reloadInterval, TimeUnit unit) {
    this.blocklistFile = blocklistFile;
    this.blocklist =
        CacheBuilder.newBuilder()
            .expireAfterAccess(reloadInterval, unit)
            .build(
                new CacheLoader<String, Set<Entry>>() {
                  @Override
                  public Set<Entry> load(String key) throws Exception {
                    File f = new File(key);
                    if (!f.exists()) {
                      return Collections.emptySet();
                    }
                    return ImmutableSet.copyOf(
                        Files.readAllLines(f.toPath()).stream()
                            .map(String::trim)
                            .filter(line -> !(line.isEmpty() || line.startsWith("#")))
                            .map(Entry::fromString)
                            .collect(Collectors.toSet()));
                  }
                });
  }

  public ReloadingFileBlocklist(String blocklistFile) {
    this(blocklistFile, DEFAULT_RELOAD_INTERVAL_SECONDS, TimeUnit.SECONDS);
  }

  @Override
  public boolean isBlocklisted(X509Certificate cert) {
    try {
      return blocklist
          .get(blocklistFile)
          .contains(new Entry(cert.getIssuerX500Principal(), cert.getSerialNumber()));
    } catch (ExecutionException ee) {
      Throwable cause = ee.getCause();
      if (cause != null) {
        if (cause instanceof RuntimeException) {
          throw (RuntimeException) cause;
        }
        throw new RuntimeException(cause);
      }
      throw new RuntimeException(ee);
    }
  }
}
