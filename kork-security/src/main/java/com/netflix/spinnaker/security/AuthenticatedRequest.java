/*
 * Copyright 2015 Netflix, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.netflix.spinnaker.security;

import static java.lang.String.format;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import lombok.SneakyThrows;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.CollectionUtils;

public class AuthenticatedRequest {
  /**
   * Known X-SPINNAKER headers, but any X-SPINNAKER-* key in the MDC will be automatically
   * propagated to the HTTP headers.
   *
   * <p>Use makeCustomerHeader() to add customer headers
   */
  public enum Header {
    USER("X-SPINNAKER-USER", true),
    ACCOUNTS("X-SPINNAKER-ACCOUNTS", true),
    USER_ORIGIN("X-SPINNAKER-USER-ORIGIN", false),
    REQUEST_ID("X-SPINNAKER-REQUEST-ID", false),
    EXECUTION_ID("X-SPINNAKER-EXECUTION-ID", false),
    APPLICATION("X-SPINNAKER-APPLICATION", false);

    private String header;
    private boolean isRequired;

    Header(String header, boolean isRequired) {
      this.header = header;
      this.isRequired = isRequired;
    }

    public String getHeader() {
      return header;
    }

    public boolean isRequired() {
      return isRequired;
    }

    public static String XSpinnakerPrefix = "X-SPINNAKER-";
    public static String XSpinnakerAnonymous = XSpinnakerPrefix + "ANONYMOUS";

    public static String makeCustomHeader(String header) {
      return XSpinnakerPrefix + header.toUpperCase();
    }
  }

  /**
   * Allow a given HTTP call to be anonymous. Normally, all requests to Spinnaker services should be
   * authenticated (i.e. include USER &amp; ACCOUNTS HTTP headers). However, in specific cases it is
   * necessary to make an anonymous call. If an anonymous call is made that is not wrapped in this
   * method, it will result in a log message and a metric being logged (indicating a potential bug).
   * Use this method to avoid the log and metric. To make an anonymous call wrap it in this
   * function, e.g.
   *
   * <pre><code>AuthenticatedRequest.allowAnonymous(() -&gt; { // do HTTP call here });</code></pre>
   */
  @SneakyThrows(Exception.class)
  public static <V> V allowAnonymous(Callable<V> closure) {
    String originalValue = MDC.get(Header.XSpinnakerAnonymous);
    MDC.put(Header.XSpinnakerAnonymous, "anonymous");

    try {
      return closure.call();
    } finally {
      setOrRemoveMdc(Header.XSpinnakerAnonymous, originalValue);
    }
  }

  public static <V> Callable<V> propagate(Callable<V> closure) {
    return propagate(closure, true, principal());
  }

  public static <V> Callable<V> propagate(Callable<V> closure, boolean restoreOriginalContext) {
    return propagate(closure, restoreOriginalContext, principal());
  }

  public static <V> Callable<V> propagate(Callable<V> closure, Object principal) {
    return propagate(closure, true, principal);
  }

  /** Ensure an appropriate MDC context is available when {@code closure} is executed. */
  public static <V> Callable<V> propagate(
      Callable<V> closure, boolean restoreOriginalContext, Object principal) {
    String spinnakerUser = getSpinnakerUser(principal).orElse(null);
    String userOrigin = getSpinnakerUserOrigin().orElse(null);
    String executionId = getSpinnakerExecutionId().orElse(null);
    String requestId = getSpinnakerRequestId().orElse(null);
    String spinnakerAccounts = getSpinnakerAccounts(principal).orElse(null);
    String spinnakerApp = getSpinnakerApplication().orElse(null);

    return () -> {
      // Deal with (set/reset) known X-SPINNAKER headers, all others will just stick around
      Map originalMdc = MDC.getCopyOfContextMap();

      try {
        setOrRemoveMdc(Header.USER.getHeader(), spinnakerUser);
        setOrRemoveMdc(Header.USER_ORIGIN.getHeader(), userOrigin);
        setOrRemoveMdc(Header.ACCOUNTS.getHeader(), spinnakerAccounts);
        setOrRemoveMdc(Header.REQUEST_ID.getHeader(), requestId);
        setOrRemoveMdc(Header.EXECUTION_ID.getHeader(), executionId);
        setOrRemoveMdc(Header.APPLICATION.getHeader(), spinnakerApp);

        return closure.call();
      } finally {
        MDC.clear();

        try {
          // force clear to avoid the potential for a memory leak if log4j is being used
          Class log4jMDC = Class.forName("org.apache.log4j.MDC");
          log4jMDC.getDeclaredMethod("clear").invoke(null);
        } catch (Exception ignored) {
        }

        if (restoreOriginalContext && originalMdc != null) {
          MDC.setContextMap(originalMdc);
        }
      }
    };
  }

  private static void setOrRemoveMdc(String key, String value) {
    if (value != null) {
      MDC.put(key, value);
    } else {
      MDC.remove(key);
    }
  }

  public static Map<String, Optional<String>> getAuthenticationHeaders() {
    Map<String, Optional<String>> headers = new HashMap<>();
    headers.put(Header.USER.getHeader(), getSpinnakerUser());
    headers.put(Header.ACCOUNTS.getHeader(), getSpinnakerAccounts());

    // Copy all headers that look like X-SPINNAKER*
    Map<String, String> allMdcEntries = MDC.getCopyOfContextMap();

    if (allMdcEntries != null) {
      for (Map.Entry<String, String> mdcEntry : allMdcEntries.entrySet()) {
        String header = mdcEntry.getKey();

        boolean isSpinnakerHeader =
            header.toLowerCase().startsWith(Header.XSpinnakerPrefix.toLowerCase());
        boolean isSpinnakerAuthHeader =
            Header.USER.getHeader().equalsIgnoreCase(header)
                || Header.ACCOUNTS.getHeader().equalsIgnoreCase(header);

        if (isSpinnakerHeader && !isSpinnakerAuthHeader) {
          headers.put(header, Optional.of(mdcEntry.getValue()));
        }
      }
    }

    return headers;
  }

  public static Optional<String> getSpinnakerUser() {
    return getSpinnakerUser(principal());
  }

  public static Optional<String> getSpinnakerUser(Object principal) {
    Object spinnakerUser = MDC.get(Header.USER.getHeader());

    if (principal != null && principal instanceof User) {
      spinnakerUser = ((User) principal).getUsername();
    }

    return Optional.ofNullable((String) spinnakerUser);
  }

  public static Optional<String> getSpinnakerAccounts() {
    return getSpinnakerAccounts(principal());
  }

  public static Optional<String> getSpinnakerAccounts(Object principal) {
    Object spinnakerAccounts = MDC.get(Header.ACCOUNTS.getHeader());

    if (principal instanceof User && !CollectionUtils.isEmpty(((User) principal).allowedAccounts)) {
      spinnakerAccounts = String.join(",", ((User) principal).getAllowedAccounts());
    }

    return Optional.ofNullable((String) spinnakerAccounts);
  }

  /**
   * Returns or creates a spinnaker request ID.
   *
   * <p>If a request ID already exists, it will be propagated without change. If a request ID does
   * not already exist:
   *
   * <p>1. If an execution ID exists, it will create a hierarchical request ID using the execution
   * ID, followed by a UUID. 2. If an execution ID does not exist, it will create a simple UUID
   * request id.
   */
  public static Optional<String> getSpinnakerRequestId() {
    return Optional.of(
        Optional.ofNullable(MDC.get(Header.REQUEST_ID.getHeader()))
            .orElse(
                getSpinnakerExecutionId()
                    .map(id -> format("%s:%s", id, UUID.randomUUID().toString()))
                    .orElse(UUID.randomUUID().toString())));
  }

  public static Optional<String> getSpinnakerUserOrigin() {
    return Optional.ofNullable(MDC.get(Header.USER_ORIGIN.getHeader()));
  }

  public static Optional<String> getSpinnakerExecutionId() {
    return Optional.ofNullable(MDC.get(Header.EXECUTION_ID.getHeader()));
  }

  public static Optional<String> getSpinnakerApplication() {
    return Optional.ofNullable(MDC.get(Header.APPLICATION.getHeader()));
  }

  /** @return the Spring Security principal or null if there is no authority. */
  private static Object principal() {
    return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
        .map(Authentication::getPrincipal)
        .orElse(null);
  }
}
