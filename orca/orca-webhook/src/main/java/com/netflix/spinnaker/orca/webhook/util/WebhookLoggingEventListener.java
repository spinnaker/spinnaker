/*
 * Copyright 2025 Salesforce, Inc.
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

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import okhttp3.Call;
import okhttp3.Connection;
import okhttp3.EventListener;
import okhttp3.Handshake;
import okhttp3.HttpUrl;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Log detailed information about each okhttp call. Log failures at warn, and other things at debug
 * or info based on a config flag. See <a href="https://github.com/square/okhttp/issues/8834">this
 * issue</a> for why we're not extending okhttp's LoggingEventListener.
 */
public class WebhookLoggingEventListener extends EventListener {
  private final Logger log = LoggerFactory.getLogger(WebhookLoggingEventListener.class);

  /**
   * Whether to include verbose information, or only the basics. Yes, this could be an int. As in,
   * we could support multiple verbosity levels. If we find out we need that, let's add it later.
   * For now, when troubleshooting setting verbose to true is sufficient.
   */
  private final boolean verbose;

  /** The start time of the call */
  private long startNs = 0;

  private enum Verbosity {
    ALWAYS,
    VERBOSE
  }

  private WebhookLoggingEventListener(boolean verbose) {
    super();
    this.verbose = verbose;
  }

  @Override
  public void cacheConditionalHit(@NotNull Call call, @NotNull Response cachedResponse) {
    logWithTime(Verbosity.VERBOSE, String.format("cacheConditionalHit: %s", cachedResponse));
  }

  @Override
  public void cacheHit(@NotNull Call call, @NotNull Response response) {
    logWithTime(Verbosity.VERBOSE, String.format("cacheHit: %s", response));
  }

  @Override
  public void cacheMiss(@NotNull Call call) {
    logWithTime(Verbosity.VERBOSE, "cacheMiss");
  }

  @Override
  public void callEnd(@NotNull Call call) {
    logWithTime(Verbosity.ALWAYS, "callEnd");
  }

  @Override
  public void callFailed(@NotNull Call call, @NotNull IOException ioe) {
    logWithTime(Verbosity.ALWAYS, String.format("callFailed: %s", ioe));
  }

  @Override
  public void callStart(@NotNull Call call) {
    startNs = System.nanoTime();

    logWithTime(Verbosity.ALWAYS, String.format("callStart: %s", call.request()));
  }

  @Override
  public void canceled(@NotNull Call call) {
    logWithTime(Verbosity.ALWAYS, "canceled");
  }

  @Override
  public void connectEnd(
      @NotNull Call call,
      @NotNull InetSocketAddress inetSocketAddress,
      @NotNull Proxy proxy,
      @Nullable Protocol protocol) {
    logWithTime(Verbosity.VERBOSE, String.format("connectEnd: %s", protocol));
  }

  @Override
  public void connectFailed(
      @NotNull Call call,
      @NotNull InetSocketAddress inetSocketAddress,
      @NotNull Proxy proxy,
      @Nullable Protocol protocol,
      @NotNull IOException ioe) {
    logWithTime(Verbosity.ALWAYS, String.format("connectFailed: %s %s", protocol, ioe));
  }

  @Override
  public void connectStart(
      @NotNull Call call, @NotNull InetSocketAddress inetSocketAddress, @NotNull Proxy proxy) {
    logWithTime(Verbosity.VERBOSE, String.format("connectStart: %s %s", inetSocketAddress, proxy));
  }

  @Override
  public void connectionAcquired(@NotNull Call call, @NotNull Connection connection) {
    logWithTime(Verbosity.VERBOSE, String.format("connectionAcquired: %s", connection));
  }

  @Override
  public void connectionReleased(@NotNull Call call, @NotNull Connection connection) {
    logWithTime(Verbosity.VERBOSE, "connectionReleased");
  }

  @Override
  public void dnsEnd(
      @NotNull Call call, @NotNull String domainName, @NotNull List<InetAddress> inetAddressList) {
    logWithTime(Verbosity.VERBOSE, String.format("dnsEnd: %s", inetAddressList));
  }

  @Override
  public void dnsStart(@NotNull Call call, @NotNull String domainName) {
    logWithTime(Verbosity.VERBOSE, String.format("dnsStart: %s", domainName));
  }

  @Override
  public void proxySelectEnd(
      @NotNull Call call, @NotNull HttpUrl url, @NotNull List<Proxy> proxies) {
    logWithTime(Verbosity.VERBOSE, String.format("proxySelectEnd: %s", proxies));
  }

  @Override
  public void proxySelectStart(@NotNull Call call, @NotNull HttpUrl url) {
    logWithTime(Verbosity.VERBOSE, String.format("proxySelectStart: %s", url));
  }

  @Override
  public void requestBodyEnd(@NotNull Call call, long byteCount) {
    // For requests with a body, this is the best evidence that we've sent a
    // request, at least via this mechanism.
    logWithTime(Verbosity.ALWAYS, String.format("requestBodyEnd: byteCount=%s", byteCount));
  }

  @Override
  public void requestBodyStart(@NotNull Call call) {
    logWithTime(Verbosity.VERBOSE, "requestBodyStart");
  }

  @Override
  public void requestFailed(@NotNull Call call, @NotNull IOException ioe) {
    logWithTime(Verbosity.ALWAYS, String.format("requestFailed: %s", ioe));
  }

  @Override
  public void requestHeadersEnd(@NotNull Call call, @NotNull Request request) {
    // This is the last thing we're guaranteed to get before the request goes
    // out on the wire, so log it always, even when not in verbose mode.  It's
    // the best evidence (at least via this mechanism) that we've sent a
    // request, since there's no explicit event for that.  We may also get
    // requestBodyStart/requestBodyEnd, but only for requests that have bodies.
    logWithTime(Verbosity.ALWAYS, "requestHeadersEnd");
  }

  @Override
  public void requestHeadersStart(@NotNull Call call) {
    logWithTime(Verbosity.VERBOSE, "requestHeadersStart");
  }

  @Override
  public void responseBodyEnd(@NotNull Call call, long byteCount) {
    logWithTime(Verbosity.ALWAYS, String.format("responseBodyEnd: byteCount=%s", byteCount));
  }

  @Override
  public void responseBodyStart(@NotNull Call call) {
    logWithTime(Verbosity.VERBOSE, "responseBodyStart");
  }

  @Override
  public void responseFailed(@NotNull Call call, @NotNull IOException ioe) {
    logWithTime(Verbosity.ALWAYS, String.format("responseFailed: %s", ioe));
  }

  @Override
  public void responseHeadersEnd(@NotNull Call call, @NotNull Response response) {
    logWithTime(Verbosity.VERBOSE, String.format("responseHeadersEnd: %s", response));
  }

  @Override
  public void responseHeadersStart(@NotNull Call call) {
    logWithTime(Verbosity.VERBOSE, "responseHeadersStart");
  }

  @Override
  public void satisfactionFailure(@NotNull Call call, @NotNull Response response) {
    logWithTime(Verbosity.ALWAYS, String.format("satisfactionFailure: %s", response));
  }

  @Override
  public void secureConnectEnd(@NotNull Call call, @Nullable Handshake handshake) {
    logWithTime(Verbosity.VERBOSE, String.format("secureConnectEnd: %s", handshake));
  }

  @Override
  public void secureConnectStart(@NotNull Call call) {
    logWithTime(Verbosity.VERBOSE, "secureConnectStart");
  }

  private void logWithTime(Verbosity verbosity, String message) {
    long timeMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
    if (verbose || (verbosity == Verbosity.ALWAYS)) {
      log.info("[{} ms] {}", timeMs, message);
    } else {
      log.debug("[{} ms] {}", timeMs, message);
    }
  }

  public static class Factory implements EventListener.Factory {
    @VisibleForTesting @Getter private final boolean verbose;

    public Factory(boolean verbose) {
      this.verbose = verbose;
    }

    @Override
    public @NotNull EventListener create(@NotNull Call call) {
      return new WebhookLoggingEventListener(verbose);
    }
  }
}
