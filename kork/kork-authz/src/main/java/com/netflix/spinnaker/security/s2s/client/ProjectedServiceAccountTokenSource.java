/*
 * Copyright 2026 DoorDash, Inc.
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

package com.netflix.spinnaker.security.s2s.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads a projected ServiceAccount token from disk. Used for the audience-bound token that outbound
 * calls present as this service's identity — the sending counterpart of {@link
 * com.netflix.spinnaker.security.s2s.provider.K8sServiceAccountTokenResolver} — and, by that
 * resolver, for the API-server-audience token that authenticates its JWKS fetch.
 *
 * <p>Kubernetes rewrites a projected token in place as it approaches expiry, so the file is re-read
 * on a short interval rather than cached for the process lifetime. The interval is a cache TTL, not
 * a correctness boundary: reading is cheap (a small local file backed by kubelet's tmpfs) and the
 * only cost of a stale read is that a token continues to be used slightly longer than necessary,
 * bounded well inside its remaining validity.
 *
 * <p>Read failures are non-fatal and yield an empty token: a caller that cannot prove its identity
 * is denied by the peer, which is the correct failure mode and keeps this safe to enable before the
 * projected volume exists. To avoid flooding logs on every request when the volume is missing, the
 * failure is logged at WARN once and at DEBUG thereafter.
 */
public class ProjectedServiceAccountTokenSource {

  private static final Logger log =
      LoggerFactory.getLogger(ProjectedServiceAccountTokenSource.class);

  private final Path tokenPath;
  private final Duration refreshInterval;

  /** Guards every mutable field below; all of them are read and written only under it. */
  private final Object lock = new Object();

  private String cachedToken;
  private long cachedAtNanos;
  private boolean loaded;
  private boolean loggedReadFailure;

  public ProjectedServiceAccountTokenSource(Path tokenPath, Duration refreshInterval) {
    this.tokenPath = tokenPath;
    this.refreshInterval = refreshInterval;
  }

  /**
   * A source that never yields a token, for deployments where service-to-service auth is off or the
   * identity travels at the transport layer. Distinct from a misconfigured path so those
   * deployments stay silent instead of warning about a file they were never meant to have.
   */
  public static ProjectedServiceAccountTokenSource disabled() {
    return new ProjectedServiceAccountTokenSource(null, Duration.ZERO);
  }

  /**
   * The current token, or empty when disabled, or when the projected volume is absent or
   * unreadable. Never throws — an unauthenticated call that the peer rejects is a better outcome
   * than an exception thrown from an interceptor on an unrelated request path.
   */
  public Optional<String> get() {
    if (tokenPath == null) {
      return Optional.empty();
    }
    synchronized (lock) {
      if (!loaded || isExpired()) {
        cachedToken = read();
        cachedAtNanos = System.nanoTime();
        loaded = true;
      }
      return Optional.ofNullable(cachedToken);
    }
  }

  private boolean isExpired() {
    return System.nanoTime() - cachedAtNanos >= refreshInterval.toNanos();
  }

  private String read() {
    try {
      String token = Files.readString(tokenPath, StandardCharsets.UTF_8).trim();
      if (token.isEmpty()) {
        return null;
      }
      loggedReadFailure = false;
      return token;
    } catch (IOException | RuntimeException e) {
      // Log the path and reason but never the file contents, which are a bearer credential.
      if (!loggedReadFailure) {
        loggedReadFailure = true;
        log.warn(
            "Unable to read projected ServiceAccount token from {} ({}); outbound calls will not "
                + "carry a service identity and peers enforcing authz.s2s will reject them. Mount an "
                + "audience-bound projected token at this path or set authz.s2s.k8s.token-path.",
            tokenPath,
            e.toString());
      } else {
        log.debug("Still unable to read projected ServiceAccount token from {}", tokenPath, e);
      }
      return null;
    }
  }
}
