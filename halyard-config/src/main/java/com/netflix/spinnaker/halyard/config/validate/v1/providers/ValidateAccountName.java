/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.halyard.config.validate.v1.providers;

import com.netflix.spinnaker.halyard.config.validate.v1.ValidateFieldRegex;

import java.util.stream.Stream;

/**
 * Ensure that an account name matches the Spinnaker restrictions on account names. Applies to all account names.
 */
public class ValidateAccountName extends ValidateFieldRegex {
  public ValidateAccountName(String subject) {
    super(subject, "^[a-z0-9]+([-a-z0-9_]*[a-z0-9])?$",
        "Must consist of alphanumeric characters optionally separated by dashes and underscores.");
  }

  @Override
  public Stream<String> validate() {
    if (subject == null) {
      return Stream.of("Must not be missing/null.");
    }

    return super.validate();
  }

  @Override
  public boolean skip() {
    return false;
  }
}
