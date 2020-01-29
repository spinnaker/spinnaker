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

import com.google.common.base.Preconditions;
import com.netflix.spinnaker.kork.common.Header;
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
        clear();

        if (restoreOriginalContext && originalMdc != null) {
          MDC.setContextMap(originalMdc);
        }
      }
    };
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
    return (principal instanceof User)
        ? Optional.ofNullable(((User) principal).getUsername())
        : get(Header.USER);
  }

  public static Optional<String> getSpinnakerAccounts() {
    return getSpinnakerAccounts(principal());
  }

  public static Optional<String> getSpinnakerAccounts(Object principal) {
    return (principal instanceof User
            && !CollectionUtils.isEmpty(((User) principal).allowedAccounts))
        ? Optional.of(String.join(",", ((User) principal).getAllowedAccounts()))
        : get(Header.ACCOUNTS);
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
        get(Header.REQUEST_ID)
            .orElse(
                getSpinnakerExecutionId()
                    .map(id -> format("%s:%s", id, UUID.randomUUID().toString()))
                    .orElse(UUID.randomUUID().toString())));
  }

  public static Optional<String> getSpinnakerExecutionType() {
    return get(Header.EXECUTION_TYPE);
  }

  public static Optional<String> getSpinnakerUserOrigin() {
    return get(Header.USER_ORIGIN);
  }

  public static Optional<String> getSpinnakerExecutionId() {
    return get(Header.EXECUTION_ID);
  }

  public static Optional<String> getSpinnakerApplication() {
    return get(Header.APPLICATION);
  }

  public static Optional<String> get(Header header) {
    return get(header.getHeader());
  }

  public static Optional<String> get(String header) {
    return Optional.ofNullable(MDC.get(header));
  }

  public static void setAccounts(String accounts) {
    set(Header.ACCOUNTS, accounts);
  }

  public static void setUser(String user) {
    set(Header.USER, user);
  }

  public static void setUserOrigin(String value) {
    set(Header.USER_ORIGIN, value);
  }

  public static void setRequestId(String value) {
    set(Header.REQUEST_ID, value);
  }

  public static void setExecutionId(String value) {
    set(Header.EXECUTION_ID, value);
  }

  public static void setApplication(String value) {
    set(Header.APPLICATION, value);
  }

  public static void setExecutionType(String value) {
    set(Header.EXECUTION_TYPE, value);
  }

  public static void set(Header header, String value) {
    set(header.getHeader(), value);
  }

  public static void set(String header, String value) {
    Preconditions.checkArgument(
        header.startsWith(Header.XSpinnakerPrefix),
        "Header '%s' does not start with 'X-SPINNAKER-'",
        header);
    MDC.put(header, value);
  }

  public static void clear() {
    MDC.clear();

    try {
      // force clear to avoid the potential for a memory leak if log4j is being used
      Class log4jMDC = Class.forName("org.apache.log4j.MDC");
      log4jMDC.getDeclaredMethod("clear").invoke(null);
    } catch (Exception ignored) {
    }
  }

  /** @return the Spring Security principal or null if there is no authority. */
  private static Object principal() {
    return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
        .map(Authentication::getPrincipal)
        .orElse(null);
  }

  private static void setOrRemoveMdc(String key, String value) {
    if (value != null) {
      MDC.put(key, value);
    } else {
      MDC.remove(key);
    }
  }
}
