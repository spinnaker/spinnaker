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

package com.netflix.spinnaker.security


import groovy.util.logging.Slf4j
import org.slf4j.MDC
import org.springframework.security.core.context.SecurityContextHolder

@Slf4j
class AuthenticatedRequest {
  public static final String SPINNAKER_USER = "X-SPINNAKER-USER"
  public static final String SPINNAKER_ACCOUNTS = "X-SPINNAKER-ACCOUNTS"

  /**
   * Ensure an appropriate MDC context is available when {@code closure} is executed.
   */
  public static final Closure propagate(
      Closure closure,
      boolean restoreOriginalContext = true,
      Object principal = SecurityContextHolder.context?.authentication?.principal) {
    def spinnakerUser = getSpinnakerUser(principal).orElse(null)
    if (!spinnakerUser) {
      return {
        MDC.remove(SPINNAKER_USER)
        MDC.remove(SPINNAKER_ACCOUNTS)
        closure()
      }
    }

    def spinnakerAccounts = getSpinnakerAccounts(principal).orElse(null)

    return {
      def originalSpinnakerUser = MDC.get(SPINNAKER_USER)
      def originalSpinnakerAccounts = MDC.get(SPINNAKER_ACCOUNTS)
      try {
        if (spinnakerUser) {
          MDC.put(SPINNAKER_USER, spinnakerUser)
        }
        if (spinnakerAccounts) {
          MDC.put(SPINNAKER_ACCOUNTS, spinnakerAccounts)
        }
        closure()
      } finally {
        MDC.clear()

        try {
          // force clear to avoid the potential for a memory leak if log4j is being used
          def log4jMDC = Class.forName("org.apache.log4j.MDC")
          log4jMDC.clear()
        } catch (Exception ignored) {
        }

        if (originalSpinnakerUser && restoreOriginalContext) {
          MDC.put(SPINNAKER_USER, originalSpinnakerUser)
        }

        if (originalSpinnakerAccounts && restoreOriginalContext) {
          MDC.put(SPINNAKER_ACCOUNTS, originalSpinnakerAccounts)
        }
      }
    }
  }

  public static Map<String, Optional<String>> getAuthenticationHeaders() {
    return [
        (SPINNAKER_USER)    : getSpinnakerUser(),
        (SPINNAKER_ACCOUNTS): getSpinnakerAccounts()
    ]
  }

  public static Optional<String> getSpinnakerUser(
      Object principal = SecurityContextHolder.context?.authentication?.principal) {
    def spinnakerUser = MDC.get(SPINNAKER_USER)

    if (principal && principal instanceof User) {
      spinnakerUser = principal.username
    }

    return Optional.ofNullable(spinnakerUser)
  }

  public static Optional<String> getSpinnakerAccounts(
      Object principal = SecurityContextHolder.context?.authentication?.principal) {
    def spinnakerAccounts = MDC.get(SPINNAKER_ACCOUNTS)

    if (principal && principal instanceof User && principal.allowedAccounts) {
      spinnakerAccounts = principal.allowedAccounts.join(",")
    }

    return Optional.ofNullable(spinnakerAccounts)
  }
}
