/*
 * Copyright 2025 Harness, Inc.
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

package com.netflix.spinnaker.credentials.definition;

/**
 * Thrown by a {@link CredentialsParser} when a definition is misconfigured in a way that must not
 * be silently skipped. {@link BasicCredentialsLoader} still loads the remaining definitions and
 * then rethrows, so the initial load fails application startup, while a later reload logs the
 * failure and keeps the credentials that were already loaded.
 */
public class InvalidCredentialsConfigurationException extends IllegalStateException {
  public InvalidCredentialsConfigurationException(String message) {
    super(message);
  }
}
