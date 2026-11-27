/*
 * Copyright 2026 spinnaker.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.kork.google.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Shared Google API authentication settings, exposed under each integration's {@code auth}. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoogleAuthProperties {
  /** Optional trusted credential configuration file. When absent, use ADC discovery. */
  private String credentialsFile;

  /** Optional full service-account email to impersonate using the selected source credentials. */
  private String impersonateServiceAccount;

  public void validate() {
    if (credentialsFile != null && credentialsFile.isBlank()) {
      throw new IllegalArgumentException("auth.credentialsFile must not be blank when configured");
    }
    if (impersonateServiceAccount != null
        && !impersonateServiceAccount.matches("[^\\s/@]+@[^\\s/@]+\\.gserviceaccount\\.com")) {
      throw new IllegalArgumentException(
          "auth.impersonateServiceAccount must be a full service-account email");
    }
  }

  /** Used by binding guards to reject removed fields without disclosing their values. */
  public static IllegalArgumentException removedProperty(String property, String replacement) {
    return new IllegalArgumentException(property + " is no longer supported; " + replacement);
  }
}
